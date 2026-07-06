package com.toolchain.promptgen;

import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.ProjectPaths;
import com.toolchain.promptgen.PromptGenModel.ClassCoverage;
import com.toolchain.promptgen.PromptGenModel.GapSource;
import com.toolchain.promptgen.PromptGenModel.MethodGap;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Parses JaCoCo/PIT XML and M2 scaffolds into per-FQN coverage gaps. */
public final class CoverageMapper {

    private static final Pattern SCAFFOLD_METHOD =
            Pattern.compile("void\\s+(\\w+)_m3Coverage\\s*\\(");

    /** Backward-compatible overload for tests without warning sink. */
    public Map<String, ClassCoverage> map(
            Path jacocoReport,
            Path pitReport,
            Path target,
            Path mirror,
            List<ClassMetadata> metadata,
            List<String> packagePrefixes) {
        return map(jacocoReport, pitReport, target, mirror, metadata, packagePrefixes, null,
                ProjectPaths.from(new java.util.Properties()));
    }

    public Map<String, ClassCoverage> map(
            Path jacocoReport,
            Path pitReport,
            Path target,
            Path mirror,
            List<ClassMetadata> metadata,
            List<String> packagePrefixes,
            List<String> warnings) {
        return map(jacocoReport, pitReport, target, mirror, metadata, packagePrefixes, warnings,
                ProjectPaths.from(new java.util.Properties()));
    }

    public Map<String, ClassCoverage> map(
            Path jacocoReport,
            Path pitReport,
            Path target,
            Path mirror,
            List<ClassMetadata> metadata,
            List<String> packagePrefixes,
            List<String> warnings,
            ProjectPaths layout) {
        Map<String, MutableGap> gaps = new LinkedHashMap<>();
        if (jacocoReport != null && Files.isRegularFile(jacocoReport)) {
            try {
                ingestJacoco(jacocoReport, packagePrefixes, gaps);
            } catch (Exception e) {
                if (warnings != null) {
                    warnings.add("JaCoCo report skipped (" + jacocoReport + "): " + e.getMessage());
                }
            }
        }
        if (pitReport != null && Files.isRegularFile(pitReport)) {
            try {
                ingestPit(pitReport, packagePrefixes, gaps);
            } catch (Exception e) {
                if (warnings != null) {
                    warnings.add("PIT report skipped (" + pitReport + "): " + e.getMessage());
                }
            }
        }
        ingestScaffolds(target, mirror, packagePrefixes, gaps, layout);
        return finalizeGaps(gaps, metadata, packagePrefixes);
    }

    private void ingestJacoco(Path report, List<String> prefixes, Map<String, MutableGap> gaps) {
        try {
            Document doc = parseXml(report);
            NodeList classes = doc.getElementsByTagName("class");
            for (int i = 0; i < classes.getLength(); i++) {
                Element clazz = (Element) classes.item(i);
                String fqn = toFqn(clazz.getAttribute("name"));
                if (!matchesPackage(fqn, prefixes)) {
                    continue;
                }
                NodeList methods = clazz.getElementsByTagName("method");
                for (int m = 0; m < methods.getLength(); m++) {
                    Element method = (Element) methods.item(m);
                    int missedBranches = counter(method, "BRANCH", true);
                    int missedLines = counter(method, "LINE", true);
                    int missedInstructions = counter(method, "INSTRUCTION", true);
                    if (missedBranches == 0 && missedLines == 0 && missedInstructions == 0) {
                        continue;
                    }
                    String methodName = decodeMethodName(method.getAttribute("name"));
                    MutableGap gap = gaps.computeIfAbsent(fqn, k -> new MutableGap(fqn));
                    MutableMethod methodGap = gap.methods.computeIfAbsent(methodName, MutableMethod::new);
                    methodGap.missedBranches = Math.max(methodGap.missedBranches, missedBranches);
                    methodGap.missedLines = Math.max(methodGap.missedLines, missedLines);
                    methodGap.sources.add(GapSource.JACOCO);
                    int line = parseInt(method.getAttribute("line"), -1);
                    if (line > 0) {
                        methodGap.lines.add(line);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JaCoCo report " + report + ": " + e.getMessage(), e);
        }
    }

    private void ingestPit(Path report, List<String> prefixes, Map<String, MutableGap> gaps) {
        try {
            Document doc = parseXml(report);
            NodeList mutations = doc.getElementsByTagName("mutation");
            for (int i = 0; i < mutations.getLength(); i++) {
                Element mutation = (Element) mutations.item(i);
                if (isDetected(mutation)) {
                    continue;
                }
                String fqn = text(mutation, "mutatedClass");
                if (!matchesPackage(fqn, prefixes)) {
                    continue;
                }
                String methodName = text(mutation, "mutatedMethod");
                String description = text(mutation, "description");
                int line = parseInt(text(mutation, "lineNumber"), -1);
                MutableGap gap = gaps.computeIfAbsent(fqn, k -> new MutableGap(fqn));
                MutableMethod methodGap = gap.methods.computeIfAbsent(methodName, MutableMethod::new);
                methodGap.sources.add(GapSource.PIT);
                if (!description.isBlank()) {
                    methodGap.mutations.add(description);
                }
                if (line > 0) {
                    methodGap.lines.add(line);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse PIT report " + report + ": " + e.getMessage(), e);
        }
    }

    private void ingestScaffolds(
            Path target,
            Path mirror,
            List<String> prefixes,
            Map<String, MutableGap> gaps,
            ProjectPaths layout) {
        scanTestTree(target, prefixes, gaps, layout);
        if (mirror != null && Files.isDirectory(mirror)) {
            scanTestTree(mirror, prefixes, gaps, layout);
        }
    }

    private void scanTestTree(Path projectRoot, List<String> prefixes, Map<String, MutableGap> gaps, ProjectPaths layout) {
        for (Path testRoot : layout.resolveTestRoots(projectRoot)) {
            if (!Files.isDirectory(testRoot)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(testRoot)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> layout.isTestSourceFile(p.getFileName().toString()))
                        .forEach(p -> ingestScaffoldFile(p, testRoot, prefixes, gaps));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to scan tests under " + testRoot, e);
            }
        }
    }

    private void ingestScaffoldFile(Path file, Path testRoot, List<String> prefixes, Map<String, MutableGap> gaps) {
        String sutFqn = sutFqnFromTestPath(testRoot, file);
        if (sutFqn == null || !matchesPackage(sutFqn, prefixes)) {
            return;
        }
        String source;
        try {
            source = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
        Matcher matcher = SCAFFOLD_METHOD.matcher(source);
        while (matcher.find()) {
            String methodName = matcher.group(1);
            MutableGap gap = gaps.computeIfAbsent(sutFqn, k -> new MutableGap(sutFqn));
            MutableMethod methodGap = gap.methods.computeIfAbsent(methodName, MutableMethod::new);
            methodGap.sources.add(GapSource.SCAFFOLD);
        }
    }

    private Map<String, ClassCoverage> finalizeGaps(
            Map<String, MutableGap> gaps,
            List<ClassMetadata> metadata,
            List<String> packagePrefixes) {
        Map<String, ClassCoverage> out = new LinkedHashMap<>();
        for (ClassMetadata meta : metadata) {
            if (!matchesPackage(meta.fqn(), packagePrefixes)) {
                continue;
            }
            MutableGap mutable = gaps.get(meta.fqn());
            if (mutable == null || mutable.methods.isEmpty()) {
                continue;
            }
            List<MethodGap> methodGaps = mutable.methods.values().stream()
                    .map(MutableMethod::toGap)
                    .sorted(Comparator.comparingInt(MethodGap::missedBranches).reversed()
                            .thenComparingInt(MethodGap::missedLines).reversed()
                            .thenComparing(MethodGap::methodName))
                    .toList();
            out.put(meta.fqn(), new ClassCoverage(meta.fqn(), methodGaps));
        }
        // Include gaps for classes present in reports/scaffolds but absent from metadata index.
        for (Map.Entry<String, MutableGap> entry : gaps.entrySet()) {
            if (out.containsKey(entry.getKey()) || !matchesPackage(entry.getKey(), packagePrefixes)) {
                continue;
            }
            List<MethodGap> methodGaps = entry.getValue().methods.values().stream()
                    .map(MutableMethod::toGap)
                    .toList();
            if (!methodGaps.isEmpty()) {
                out.put(entry.getKey(), new ClassCoverage(entry.getKey(), methodGaps));
            }
        }
        return out;
    }

    static List<String> parsePrefixes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    static boolean matchesPackage(String fqn, List<String> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) {
            return true;
        }
        for (String prefix : prefixes) {
            if (fqn.equals(prefix) || fqn.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    static String sutFqnFromTestPath(Path testRoot, Path testFile) {
        Path rel = testRoot.relativize(testFile);
        String name = rel.getFileName().toString();
        if (name.endsWith("Test.java")) {
            name = name.substring(0, name.length() - "Test.java".length());
        } else if (name.endsWith("Tests.java")) {
            name = name.substring(0, name.length() - "Tests.java".length());
        } else {
            return null;
        }
        Path parent = rel.getParent();
        String pkg = parent == null ? "" : parent.toString().replace('\\', '/').replace('/', '.');
        return pkg.isEmpty() ? name : pkg + "." + name;
    }

    private Document parseXml(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setValidating(false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return factory.newDocumentBuilder().parse(file.toFile());
    }

    private int counter(Element method, String type, boolean missed) {
        NodeList counters = method.getElementsByTagName("counter");
        for (int i = 0; i < counters.getLength(); i++) {
            Element counter = (Element) counters.item(i);
            if (type.equals(counter.getAttribute("type"))) {
                return parseInt(counter.getAttribute(missed ? "missed" : "covered"), 0);
            }
        }
        return 0;
    }

    private boolean isDetected(Element mutation) {
        String detected = mutation.getAttribute("detected");
        if ("true".equalsIgnoreCase(detected)) {
            return true;
        }
        String status = mutation.getAttribute("status");
        return "KILLED".equalsIgnoreCase(status);
    }

    private String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }

    private String toFqn(String jacocoName) {
        return jacocoName.replace('/', '.');
    }

    private String decodeMethodName(String raw) {
        if ("<init>".equals(raw)) {
            return "<constructor>";
        }
        return raw;
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static final class MutableGap {
        final String fqn;
        final Map<String, MutableMethod> methods = new LinkedHashMap<>();

        MutableGap(String fqn) {
            this.fqn = fqn;
        }
    }

    private static final class MutableMethod {
        final String methodName;
        int missedBranches;
        int missedLines;
        final List<Integer> lines = new ArrayList<>();
        final List<String> mutations = new ArrayList<>();
        final List<GapSource> sources = new ArrayList<>();

        MutableMethod(String methodName) {
            this.methodName = methodName;
        }

        MethodGap toGap() {
            return new MethodGap(
                    methodName,
                    missedBranches,
                    missedLines,
                    lines.stream().distinct().sorted().toList(),
                    mutations.stream().distinct().toList(),
                    sources.stream().distinct().toList());
        }
    }
}
