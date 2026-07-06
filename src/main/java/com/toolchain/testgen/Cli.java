package com.toolchain.testgen;

import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.ProjectPaths;
import com.toolchain.testgen.TestGenModel.Placement;
import com.toolchain.testgen.TestGenModel.Report;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/** M2 entry point. Loads config, generates skeletons, wraps once into [Success, Data, Error] -> exit 0/1. */
public final class Cli {

    public static void main(String[] args) {
        Result result = execute(args);
        if (result.success()) {
            long target = count(result.data(), Placement.TARGET);
            long mirror = count(result.data(), Placement.LOCAL_MIRROR);
            long skipped = count(result.data(), Placement.SKIPPED);
            System.out.println("[SUCCESS] " + target + " test(s) into target, " + mirror
                    + " into local mirror (not overwritten), " + skipped + " skipped. See report.");
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
                        + "or pass --target=<path>. M1 and M2 share target.path.");
            }
            Path mirror = Path.of(props.getProperty("testgen.mirror.dir", "output/tests-local-mirror"));
            Path metadataDir = Path.of(props.getProperty("metadata.dir", "output/metadata"));
            Path reportPath = Path.of(arg(args, "--report", props.getProperty("testgen.report", "output/testgen-report.md")));

            List<ClassMetadata> metadata = new MetadataLoader().load(metadataDir);
            ProjectPaths layout = ProjectPaths.from(props);
            TestGenPipeline pipeline = new TestGenPipeline();
            Report report = pipeline.run(Path.of(target), mirror, metadata, layout);

            writeReport(reportPath, pipeline.renderReport(report));
            System.out.println("Report -> " + reportPath.toAbsolutePath());
            return Result.ok(report);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    private static long count(Report report, Placement placement) {
        return report.outcomes().stream().filter(o -> o.placement() == placement).count();
    }

    /**
     * Target precedence (highest first): application.properties {@code target.path} (shared with M1),
     * then {@code --target=} CLI arg. Returns {@code null} when none are set (caller fails fast).
     */
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

    private static void writeReport(Path reportPath, String content) {
        try {
            if (reportPath.getParent() != null) {
                Files.createDirectories(reportPath.getParent());
            }
            Files.writeString(reportPath, content);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write report to " + reportPath + ": " + e.getMessage(), e);
        }
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
            // Config optional; fall back to defaults.
        }
        return props;
    }

    /** M2 boundary [Success, Data, Error] contract. */
    public record Result(boolean success, Report data, String error) {
        public static Result ok(Report data) {
            return new Result(true, data, null);
        }

        public static Result fail(String error) {
            return new Result(false, null, error);
        }
    }
}
