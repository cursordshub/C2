package com.toolchain.metadata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Target project layout: configurable production and test source roots relative to {@code target.path}.
 * Shared across M1 (scan), M2 (write + collision), and M3 (read scaffolds).
 */
public final class ProjectPaths {

    public static final String SOURCE_ROOTS_KEY = "target.source.roots";
    public static final String TEST_ROOTS_KEY = "target.test.roots";
    public static final String TEST_SUFFIXES_KEY = "target.test.file.suffixes";

    private static final String DEFAULT_SOURCE_ROOTS = "src/main/java";
    private static final String DEFAULT_TEST_ROOTS = "src/test/java";
    private static final String DEFAULT_TEST_SUFFIXES = "Test.java,Tests.java,IT.java";

    private final List<String> sourceRoots;
    private final List<String> testRoots;
    private final List<String> testSuffixes;

    public ProjectPaths(List<String> sourceRoots, List<String> testRoots, List<String> testSuffixes) {
        this.sourceRoots = List.copyOf(sourceRoots);
        this.testRoots = List.copyOf(testRoots);
        this.testSuffixes = List.copyOf(testSuffixes);
    }

    public static ProjectPaths from(Properties props) {
        return new ProjectPaths(
                parseRoots(props.getProperty(SOURCE_ROOTS_KEY, DEFAULT_SOURCE_ROOTS)),
                parseRoots(props.getProperty(TEST_ROOTS_KEY, DEFAULT_TEST_ROOTS)),
                parseRoots(props.getProperty(TEST_SUFFIXES_KEY, DEFAULT_TEST_SUFFIXES)));
    }

    public List<String> sourceRoots() {
        return sourceRoots;
    }

    public List<String> testRoots() {
        return testRoots;
    }

    public List<String> testSuffixes() {
        return testSuffixes;
    }

    public boolean isTestSourceFile(String fileName) {
        for (String suffix : testSuffixes) {
            if (fileName.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    public List<Path> resolveSourceRoots(Path targetRoot) {
        return sourceRoots.stream().map(r -> targetRoot.resolve(r)).toList();
    }

    public List<Path> resolveTestRoots(Path targetRoot) {
        return testRoots.stream().map(r -> targetRoot.resolve(r)).toList();
    }

    /** Primary relative test path for generated files (forward slashes), e.g. {@code test/com/acme/FooTest.java}. */
    public String primaryTestRelPath(String fqn) {
        if (usesNestedTestLayout(fqn)) {
            return nestedTestFileRelPath(fqn);
        }
        return testFileRelPath(fqn);
    }

    public String testFileRelPath(String fqn) {
        String simple = simpleName(fqn);
        return normalize(testRoots.get(0) + "/" + packagePath(fqn) + simple + "Test.java");
    }

    /** Inner types: {@code .../PricingEngine/PremiumPricingRuleTest.java} under the configured test root. */
    public String nestedTestFileRelPath(String fqn) {
        String simple = simpleName(fqn);
        int dot = fqn.lastIndexOf('.');
        if (dot < 0) {
            return normalize(testRoots.get(0) + "/" + simple + "Test.java");
        }
        String outer = fqn.substring(0, dot);
        String outerSimple = simpleName(outer);
        return normalize(testRoots.get(0) + "/" + packagePath(outer) + outerSimple + "/" + simple + "Test.java");
    }

    public List<String> testFileRelCandidates(String fqn) {
        List<String> candidates = new ArrayList<>();
        candidates.add(testFileRelPath(fqn));
        if (usesNestedTestLayout(fqn)) {
            candidates.add(nestedTestFileRelPath(fqn));
        }
        return candidates;
    }

    public Set<String> testFileNamesFor(String simpleClassName) {
        Set<String> names = new LinkedHashSet<>();
        for (String suffix : testSuffixes) {
            names.add(simpleClassName + suffix);
        }
        return names;
    }

    public boolean usesNestedTestLayout(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        if (lastDot <= 0) {
            return false;
        }
        int prevDot = fqn.lastIndexOf('.', lastDot - 1);
        if (prevDot < 0) {
            return false;
        }
        String penultimate = fqn.substring(prevDot + 1, lastDot);
        return !penultimate.isEmpty() && Character.isUpperCase(penultimate.charAt(0));
    }

    public boolean hasConfiguredSourceTree(Path targetRoot) {
        return resolveSourceRoots(targetRoot).stream().anyMatch(Files::isDirectory);
    }

    private static List<String> parseRoots(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim().replace('\\', '/');
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out.isEmpty() ? List.of(DEFAULT_SOURCE_ROOTS) : out;
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }

    private String packagePath(String fqn) {
        String pkg = packageName(fqn);
        return pkg.isEmpty() ? "" : pkg.replace('.', '/') + "/";
    }

    private String packageName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? "" : fqn.substring(0, dot);
    }

    private String simpleName(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }
}
