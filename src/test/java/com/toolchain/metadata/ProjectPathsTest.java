package com.toolchain.metadata;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectPathsTest {

    @Test
    void defaultsMatchMavenLayout() {
        ProjectPaths layout = ProjectPaths.from(new Properties());
        assertEquals("src/main/java", layout.sourceRoots().get(0));
        assertEquals("src/test/java", layout.testRoots().get(0));
        assertEquals("src/test/java/com/acme/FooTest.java", layout.testFileRelPath("com.acme.Foo"));
    }

    @Test
    void flatLayoutUsesTestRootDirectly() {
        Properties props = new Properties();
        props.setProperty(ProjectPaths.SOURCE_ROOTS_KEY, "src");
        props.setProperty(ProjectPaths.TEST_ROOTS_KEY, "test");
        ProjectPaths layout = ProjectPaths.from(props);

        assertEquals("test/com/acme/FooTest.java", layout.testFileRelPath("com.acme.Foo"));
        assertEquals("test/com/acme/PricingEngine/PremiumPricingRuleTest.java",
                layout.nestedTestFileRelPath("com.acme.PricingEngine.PremiumPricingRule"));
    }

    @Test
    void nestedInnerClassUsesOuterDirectory() {
        ProjectPaths layout = ProjectPaths.from(new Properties());
        assertTrue(layout.usesNestedTestLayout("com.sample.billing.PricingEngine.PremiumPricingRule"));
        assertEquals(
                "src/test/java/com/sample/billing/PricingEngine/PremiumPricingRuleTest.java",
                layout.primaryTestRelPath("com.sample.billing.PricingEngine.PremiumPricingRule"));
    }

    @Test
    void testFileNamesIncludeConfiguredSuffixes() {
        Properties props = new Properties();
        props.setProperty(ProjectPaths.TEST_SUFFIXES_KEY, "Test.java,IT.java");
        ProjectPaths layout = ProjectPaths.from(props);
        assertTrue(layout.testFileNamesFor("CheckoutService").contains("CheckoutServiceTest.java"));
        assertTrue(layout.testFileNamesFor("CheckoutService").contains("CheckoutServiceIT.java"));
        assertFalse(layout.testFileNamesFor("CheckoutService").contains("CheckoutServiceTests.java"));
    }

    @Test
    void isTestSourceFileMatchesSuffixes() {
        ProjectPaths layout = ProjectPaths.from(new Properties());
        assertTrue(layout.isTestSourceFile("FooTest.java"));
        assertTrue(layout.isTestSourceFile("BarIT.java"));
        assertFalse(layout.isTestSourceFile("Foo.java"));
    }
}
