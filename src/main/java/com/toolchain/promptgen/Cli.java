package com.toolchain.promptgen;

import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.ProjectPaths;
import com.toolchain.promptgen.PromptGenModel.ClassCoverage;
import com.toolchain.promptgen.PromptGenModel.PromptArtifact;
import com.toolchain.promptgen.PromptGenModel.Report;
import com.toolchain.promptgen.PromptGenModel.SkippedClass;
import com.toolchain.testgen.MetadataLoader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/** M3 entry point. Consumes M1 metadata + coverage reports + M2 skeletons; emits chat-ready prompts. */
public final class Cli {

    public static void main(String[] args) {
        Result result = execute(args);
        if (result.success()) {
            Report report = result.data();
            System.out.println("[SUCCESS] " + report.prompts().size() + " prompt(s) written, "
                    + report.skipped().size() + " skipped. See report.");
            System.exit(0);
        }
        System.err.println("[ERROR] " + result.error());
        System.exit(1);
    }

    static Result execute(String[] args) {
        try {
            Properties props = loadProperties();
            String target = resolveTarget(args, props);
            if (target == null) {
                return Result.fail("No target configured. Set target.path in application.properties "
                        + "or pass --target=<path>. M1/M2/M3 share target.path.");
            }
            return run(Path.of(target), props);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /** Full M3 pipeline — used by tests with overridden properties. */
    static Result run(Path targetPath, Properties props) {
        try {
            Path metadataDir = Path.of(props.getProperty("metadata.dir", "output/metadata"));
            Path mirror = Path.of(props.getProperty("testgen.mirror.dir", "output/tests-local-mirror"));
            Path outDir = Path.of(props.getProperty("promptgen.output.dir", "output/prompts"));
            Path reportPath = Path.of(props.getProperty("promptgen.report", "output/promptgen-report.md"));
            List<String> prefixes = CoverageMapper.parsePrefixes(
                    props.getProperty("promptgen.internal.packages",
                            props.getProperty("dependency-graph.internal.packages", "")));

            List<ClassMetadata> metadata = new MetadataLoader().load(metadataDir);
            Map<String, ClassMetadata> index = new LinkedHashMap<>();
            metadata.forEach(m -> index.put(m.fqn(), m));

            Path jacoco = resolveReportPath(props, "promptgen.jacoco.report",
                    targetPath.resolve("build/reports/jacoco/test/jacocoTestReport.xml"),
                    Path.of("build/reports/jacoco/test/jacocoTestReport.xml"));
            Path pit = resolveReportPath(props, "promptgen.pitest.report",
                    targetPath.resolve("build/reports/pitest/mutations.xml"),
                    Path.of("build/reports/pitest/mutations.xml"));

            List<String> warnings = new ArrayList<>();
            if (jacoco == null) {
                warnings.add("JaCoCo report not found — using scaffold/PIT gaps only.");
            }
            if (pit == null) {
                warnings.add("PIT mutations.xml not found — using JaCoCo/scaffold gaps only.");
            }

            Map<String, ClassCoverage> coverage = new CoverageMapper().map(
                    jacoco, pit, targetPath, mirror, metadata, prefixes, warnings,
                    ProjectPaths.from(props));

            List<PromptArtifact> prompts = new PromptBuilder().build(
                    index, coverage, targetPath, mirror, ProjectPaths.from(props));
            Set<String> prompted = prompts.stream().map(PromptArtifact::sutFqn).collect(Collectors.toSet());
            List<SkippedClass> skipped = buildSkipped(index, coverage, prompted, prefixes);

            Report report = new Report(prompts, skipped, warnings);
            PromptWriter writer = new PromptWriter();
            writer.writePrompts(outDir, prompts);
            writer.writeReport(reportPath, report);
            System.out.println("Report -> " + reportPath.toAbsolutePath());
            return Result.ok(report);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    private static List<SkippedClass> buildSkipped(
            Map<String, ClassMetadata> index,
            Map<String, ClassCoverage> coverage,
            Set<String> prompted,
            List<String> prefixes) {
        List<SkippedClass> skipped = new ArrayList<>();
        for (ClassMetadata meta : index.values()) {
            if (!CoverageMapper.matchesPackage(meta.fqn(), prefixes)) {
                continue;
            }
            if (prompted.contains(meta.fqn())) {
                continue;
            }
            if (!coverage.containsKey(meta.fqn())) {
                skipped.add(new SkippedClass(meta.fqn(), "no coverage gaps detected (JaCoCo/PIT/scaffold)"));
            } else {
                skipped.add(new SkippedClass(meta.fqn(), "gaps found but no M2 test skeleton located"));
            }
        }
        return skipped;
    }

    private static Path resolveReportPath(Properties props, String key, Path targetDefault, Path toolDefault) {
        String configured = props.getProperty(key, "").trim();
        if (!configured.isBlank()) {
            Path path = Path.of(configured);
            return Files.isRegularFile(path) ? path : null;
        }
        if (Files.isRegularFile(targetDefault)) {
            return targetDefault;
        }
        if (Files.isRegularFile(toolDefault)) {
            return toolDefault;
        }
        return null;
    }

    private static String resolveTarget(String[] args, Properties props) {
        String fromProps = props.getProperty("target.path", "");
        if (!fromProps.isBlank()) {
            return fromProps;
        }
        String fromArg = arg(args, "--target", null);
        if (fromArg != null && !fromArg.isBlank()) {
            return fromArg;
        }
        return null;
    }

    private static String arg(String[] args, String key, String fallback) {
        for (String a : args) {
            if (a.startsWith(key + "=")) {
                return a.substring(key.length() + 1);
            }
        }
        return fallback;
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = Cli.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception e) {
            // Optional config.
        }
        return props;
    }

    public record Result(boolean success, Report data, String error) {
        public static Result ok(Report data) {
            return new Result(true, data, null);
        }

        public static Result fail(String error) {
            return new Result(false, null, error);
        }
    }
}
