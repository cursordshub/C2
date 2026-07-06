package com.toolchain;

import com.toolchain.metadata.MetadataPipeline;
import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.Model.ClassType;
import com.toolchain.metadata.Model.ExtractionResult;
import com.toolchain.metadata.ProjectPaths;
import com.toolchain.promptgen.CoverageMapper;
import com.toolchain.promptgen.PromptBuilder;
import com.toolchain.promptgen.PromptGenModel.ClassCoverage;
import com.toolchain.promptgen.PromptGenModel.PromptArtifact;
import com.toolchain.testgen.TestGenModel.Placement;
import com.toolchain.testgen.TestGenModel.Report;
import com.toolchain.testgen.TestGenPipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check for flat target layout from project root:
 * {@code src/<packages>/...} production sources and {@code test/<packages>/...} tests.
 */
class FlatLayoutIntegrationTest {

    private static Properties flatLayoutProps() {
        Properties props = new Properties();
        props.setProperty(ProjectPaths.SOURCE_ROOTS_KEY, "src");
        props.setProperty(ProjectPaths.TEST_ROOTS_KEY, "test");
        return props;
    }

    @Test
    void m1ScansSrcOnlyNotTestTree(@TempDir Path root) throws Exception {
        Path target = writeFlatTarget(root);

        ProjectPaths layout = ProjectPaths.from(flatLayoutProps());
        ExtractionResult result = new MetadataPipeline().run(target, layout);

        assertTrue(result.byFqn().containsKey("com.acme.app.Widget"));
        assertTrue(result.byFqn().containsKey("com.acme.app.Helper"));
        assertFalse(result.byFqn().containsKey("com.acme.app.WidgetTest"),
                "M1 must not treat test/ tree as production source");
    }

    @Test
    void m2WritesNewTestsUnderTestRootAndMirrorUsesSameShape(@TempDir Path root) throws Exception {
        Path target = writeFlatTarget(root);
        Path mirror = root.resolve("mirror");
        ProjectPaths layout = ProjectPaths.from(flatLayoutProps());

        ExtractionResult m1 = new MetadataPipeline().run(target, layout);
        List<ClassMetadata> metadata = List.copyOf(m1.byFqn().values());

        Report report = new TestGenPipeline().run(target, mirror, metadata, layout);

        assertEquals(Placement.LOCAL_MIRROR, placement(report, "com.acme.app.Widget"),
                "existing test/com/acme/app/WidgetTest.java must not be overwritten");
        assertEquals(Placement.TARGET, placement(report, "com.acme.app.Helper"),
                "no existing Helper test -> write into target test tree");

        Path helperTest = target.resolve("test/com/acme/app/HelperTest.java");
        Path mirrorWidget = mirror.resolve("test/com/acme/app/WidgetTest.java");

        assertTrue(Files.isRegularFile(helperTest),
                "new test at target/test/<packages>/HelperTest.java");
        assertTrue(Files.isRegularFile(mirrorWidget),
                "mirror at mirror/test/<packages>/WidgetTest.java");
        assertFalse(Files.exists(mirror.resolve("src/test/java/com/acme/app/WidgetTest.java")),
                "mirror must not use Maven src/test/java prefix");

        String rel = report.outcomes().stream()
                .filter(o -> o.sutFqn().equals("com.acme.app.Helper"))
                .map(o -> o.relPath())
                .findFirst()
                .orElseThrow();
        assertEquals("test/com/acme/app/HelperTest.java", rel);
    }

    @Test
    void m3FindsScaffoldsUnderTestRoot(@TempDir Path root) throws Exception {
        Path target = writeFlatTarget(root);
        ProjectPaths layout = ProjectPaths.from(flatLayoutProps());

        ClassMetadata widget = new com.toolchain.metadata.Model.ClassMetadata(
                "com.acme.app.Widget", ClassType.CLASS, false,
                List.of(), Map.of(), List.of(), List.of(), List.of());

        Map<String, ClassCoverage> coverage = new CoverageMapper().map(
                null, null, target, null, List.of(widget), List.of("com.acme"), null, layout);

        assertTrue(coverage.containsKey("com.acme.app.Widget"),
                "M3 scaffold scan must read test/com/acme/app/WidgetTest.java");

        List<PromptArtifact> prompts = new PromptBuilder().build(
                Map.of(widget.fqn(), widget),
                coverage,
                target,
                null,
                layout);

        assertEquals(1, prompts.size());
        assertEquals("test/com/acme/app/WidgetTest.java", prompts.get(0).testRelPath());
    }

    private static Path writeFlatTarget(Path root) throws Exception {
        Path target = root.resolve("flat-target");

        Path widgetSrc = target.resolve("src/com/acme/app/Widget.java");
        Files.createDirectories(widgetSrc.getParent());
        Files.writeString(widgetSrc, """
                package com.acme.app;

                public class Widget {
                    public String label() {
                        return "widget";
                    }
                }
                """);

        Path helperSrc = target.resolve("src/com/acme/app/Helper.java");
        Files.writeString(helperSrc, """
                package com.acme.app;

                public class Helper {
                    public int count() {
                        return 1;
                    }
                }
                """);

        Path widgetTest = target.resolve("test/com/acme/app/WidgetTest.java");
        Files.createDirectories(widgetTest.getParent());
        Files.writeString(widgetTest, """
                package com.acme.app;

                class WidgetTest {
                    void label_m3Coverage() {}
                }
                """);

        return target;
    }

    private static Placement placement(Report report, String fqn) {
        return report.outcomes().stream()
                .filter(o -> o.sutFqn().equals(fqn))
                .map(o -> o.placement())
                .findFirst()
                .orElseThrow();
    }
}
