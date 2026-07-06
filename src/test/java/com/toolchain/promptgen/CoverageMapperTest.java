package com.toolchain.promptgen;

import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.Model.ClassType;
import com.toolchain.metadata.ProjectPaths;
import com.toolchain.promptgen.PromptGenModel.ClassCoverage;
import com.toolchain.promptgen.PromptGenModel.GapSource;
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

class CoverageMapperTest {

    private final CoverageMapper mapper = new CoverageMapper();

    @Test
    void parsesJacocoMissedBranches(@TempDir Path dir) throws Exception {
        Path xml = dir.resolve("jacoco.xml");
        Files.writeString(xml, """
                <?xml version="1.0" encoding="UTF-8"?>
                <report name="demo">
                  <package name="com/sample/billing">
                    <class name="com/sample/billing/CheckoutService" sourcefilename="CheckoutService.java">
                      <method name="completeCheckout" desc="()V" line="74">
                        <counter type="BRANCH" missed="3" covered="1"/>
                        <counter type="LINE" missed="5" covered="2"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        Map<String, ClassCoverage> gaps = mapper.map(
                xml, null, dir, null, List.of(), List.of("com.sample"));

        assertTrue(gaps.containsKey("com.sample.billing.CheckoutService"));
        assertEquals("completeCheckout", gaps.get("com.sample.billing.CheckoutService").gaps().get(0).methodName());
        assertEquals(3, gaps.get("com.sample.billing.CheckoutService").gaps().get(0).missedBranches());
        assertTrue(gaps.get("com.sample.billing.CheckoutService").gaps().get(0).sources().contains(GapSource.JACOCO));
    }

    @Test
    void parsesPitSurvivingMutations(@TempDir Path dir) throws Exception {
        Path xml = dir.resolve("mutations.xml");
        Files.writeString(xml, """
                <?xml version="1.0" encoding="UTF-8"?>
                <mutations>
                  <mutation detected='false' status='SURVIVED'>
                    <mutatedClass>com.sample.billing.CheckoutService</mutatedClass>
                    <mutatedMethod>completeCheckout</mutatedMethod>
                    <lineNumber>80</lineNumber>
                    <description>negated conditional</description>
                  </mutation>
                </mutations>
                """);

        Map<String, ClassCoverage> gaps = mapper.map(
                null, xml, dir, null, List.of(), List.of("com.sample"));

        assertEquals("negated conditional",
                gaps.get("com.sample.billing.CheckoutService").gaps().get(0).survivingMutations().get(0));
        assertTrue(gaps.get("com.sample.billing.CheckoutService").gaps().get(0).sources().contains(GapSource.PIT));
    }

    @Test
    void detectsM3ScaffoldsInTargetTests(@TempDir Path dir) throws Exception {
        Path testFile = dir.resolve("src/test/java/com/sample/billing/CheckoutServiceTest.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, """
                class CheckoutServiceTest {
                    void calculateShippingFee_m3Coverage() {}
                    void completeCheckout_m3Coverage() {}
                }
                """);

        ClassMetadata meta = new ClassMetadata(
                "com.sample.billing.CheckoutService", ClassType.CLASS, false,
                List.of(), Map.of(), List.of(), List.of(), List.of());

        Map<String, ClassCoverage> gaps = mapper.map(
                null, null, dir, null, List.of(meta), List.of("com.sample"));

        assertEquals(2, gaps.get("com.sample.billing.CheckoutService").gaps().size());
        assertTrue(gaps.get("com.sample.billing.CheckoutService").gaps().stream()
                .allMatch(g -> g.sources().contains(GapSource.SCAFFOLD)));
    }

    @Test
    void detectsM3ScaffoldsInFlatTestRoot(@TempDir Path dir) throws Exception {
        Properties props = new Properties();
        props.setProperty(ProjectPaths.TEST_ROOTS_KEY, "test");
        Path testFile = dir.resolve("test/com/sample/billing/CheckoutServiceTest.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, """
                class CheckoutServiceTest {
                    void completeCheckout_m3Coverage() {}
                }
                """);

        ClassMetadata meta = new ClassMetadata(
                "com.sample.billing.CheckoutService", ClassType.CLASS, false,
                List.of(), Map.of(), List.of(), List.of(), List.of());

        Map<String, ClassCoverage> gaps = mapper.map(
                null, null, dir, null, List.of(meta), List.of("com.sample"), null,
                ProjectPaths.from(props));

        assertEquals(1, gaps.get("com.sample.billing.CheckoutService").gaps().size());
    }

    @Test
    void packageFilterExcludesOtherPackages(@TempDir Path dir) throws Exception {
        Path xml = dir.resolve("jacoco.xml");
        Files.writeString(xml, """
                <?xml version="1.0" encoding="UTF-8"?>
                <report name="demo">
                  <package name="com/other">
                    <class name="com/other/Widget" sourcefilename="Widget.java">
                      <method name="run" desc="()V" line="1">
                        <counter type="LINE" missed="1" covered="0"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        Map<String, ClassCoverage> gaps = mapper.map(
                xml, null, dir, null, List.of(), List.of("com.sample"));

        assertFalse(gaps.containsKey("com.other.Widget"));
    }

    @Test
    void sutFqnFromTestPathResolvesNestedClasses() {
        Path root = Path.of("src/test/java");
        Path file = Path.of("src/test/java/com/sample/billing/PricingEngine/PremiumPricingRuleTest.java");
        assertEquals("com.sample.billing.PricingEngine.PremiumPricingRule",
                CoverageMapper.sutFqnFromTestPath(root, file));
    }
}
