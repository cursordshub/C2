package com.toolchain.testgen;

import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.Model.ClassType;
import com.toolchain.metadata.Model.ConstructorInfo;
import com.toolchain.metadata.ProjectPaths;
import com.toolchain.testgen.TestGenModel.Placement;
import com.toolchain.testgen.TestGenModel.Report;
import com.toolchain.testgen.TestGenModel.TestOutcome;
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

/** Exercises the real placement / non-overwrite logic + report. (Scaffolding until STATE 3.) */
class TestGenPipelineTest {

    private static ClassMetadata cls(String fqn, ClassType type, ConstructorInfo... ctors) {
        return new ClassMetadata(fqn, type, false, List.of(), Map.of(), List.of(), List.of(), List.of(ctors));
    }

    private static List<ClassMetadata> sampleMetadata() {
        return List.of(
                cls("com.sample.Order", ClassType.CLASS,
                        new ConstructorInfo(List.of("String", "Customer", "PricingService"), "public")),
                cls("com.sample.Customer", ClassType.CLASS,
                        new ConstructorInfo(List.of("String", "String"), "public")),
                cls("com.sample.PricingService", ClassType.CLASS),
                cls("com.sample.PaymentStatus", ClassType.ENUM),
                cls("com.sample.OrderRepository", ClassType.INTERFACE));
    }

    @Test
    void newTestsGoToTargetExistingGoToMirrorAndKindsAreSkipped(@TempDir Path root) throws Exception {
        Path target = root.resolve("target");
        Path mirror = root.resolve("mirror");
        // Pre-existing hand-written test in the target -> must NOT be overwritten.
        Path existing = target.resolve("src/test/java/com/sample/CustomerTest.java");
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "package com.sample; class CustomerTest {}");

        Report report = new TestGenPipeline().run(target, mirror, sampleMetadata());

        assertEquals(Placement.TARGET, placement(report, "com.sample.Order"));
        assertEquals(Placement.TARGET, placement(report, "com.sample.PricingService"));
        assertEquals(Placement.LOCAL_MIRROR, placement(report, "com.sample.Customer"),
                "existing target test must not be overwritten");
        assertEquals(Placement.SKIPPED, placement(report, "com.sample.PaymentStatus"), "enum skipped");
        assertEquals(Placement.SKIPPED, placement(report, "com.sample.OrderRepository"), "interface skipped");

        assertTrue(Files.exists(target.resolve("src/test/java/com/sample/OrderTest.java")),
                "new test written into target tree");
        assertTrue(Files.exists(mirror.resolve("src/test/java/com/sample/CustomerTest.java")),
                "colliding test written into local mirror");
        // Original target test untouched.
        assertEquals("package com.sample; class CustomerTest {}", Files.readString(existing));

        String rendered = new TestGenPipeline().renderReport(report);
        assertTrue(rendered.contains("CustomerTest"), "report lists the locally-created (collision) file");
        assertTrue(rendered.toLowerCase().contains("do not overwrite")
                || rendered.toLowerCase().contains("local mirror"), "report warns about non-overwrite");
    }

    @Test
    void flatLayoutWritesUnderConfiguredTestRoot(@TempDir Path root) throws Exception {
        Properties props = new Properties();
        props.setProperty(ProjectPaths.TEST_ROOTS_KEY, "test");
        ProjectPaths layout = ProjectPaths.from(props);

        Path target = root.resolve("target");
        Path mirror = root.resolve("mirror");
        Path existing = target.resolve("test/com/sample/CustomerTest.java");
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "package com.sample; class CustomerTest {}");

        Report report = new TestGenPipeline().run(target, mirror, sampleMetadata(), layout);

        assertEquals(Placement.LOCAL_MIRROR, placement(report, "com.sample.Customer"));
        assertTrue(Files.exists(target.resolve("test/com/sample/OrderTest.java")),
                "new test under flat test/ root");
        assertTrue(Files.exists(mirror.resolve("test/com/sample/CustomerTest.java")),
                "mirror uses same test/ layout, not src/test/java");
        assertFalse(Files.exists(mirror.resolve("src/test/java/com/sample/CustomerTest.java")),
                "mirror must not hardcode Maven test path");
    }

    private static Placement placement(Report report, String fqn) {
        return report.outcomes().stream().filter(o -> o.sutFqn().equals(fqn))
                .map(TestOutcome::placement).findFirst().orElseThrow();
    }
}
