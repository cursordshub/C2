package com.toolchain.metadata;

import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.Model.ClassType;
import com.toolchain.metadata.Model.DependencyGraph;
import com.toolchain.metadata.Model.Edge;
import com.toolchain.metadata.Model.ExtractionResult;
import com.toolchain.metadata.Model.Result;
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

class DependencyGraphYamlWriterTest {

    private final DependencyGraphYamlWriter writer = new DependencyGraphYamlWriter();

    @Test
    void disabledByDefaultWithoutConfigFlag() {
        assertFalse(writer.enabled(new Properties()));
    }

    @Test
    void enabledWhenConfigFlagTrue() {
        Properties props = new Properties();
        props.setProperty(DependencyGraphYamlWriter.ENABLED_KEY, "true");
        assertTrue(writer.enabled(props));
    }

    @Test
    void rendersClassesWithInternalDepsOrderedByUsage() {
        ClassMetadata checkout = new ClassMetadata(
                "com.sample.billing.CheckoutService",
                ClassType.CLASS,
                false,
                List.of("TaxCalculator", "BillingContext"),
                Map.of("TaxCalculator", 5, "BillingContext", 2, "String", 10),
                List.of(),
                List.of(),
                List.of());

        ClassMetadata tax = new ClassMetadata(
                "com.sample.api.TaxCalculator",
                ClassType.INTERFACE,
                false,
                List.of("BillingContext"),
                Map.of("BillingContext", 1),
                List.of(),
                List.of(),
                List.of());

        ClassMetadata billingCtx = new ClassMetadata(
                "com.sample.billing.BillingContext",
                ClassType.CLASS,
                false,
                List.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of());

        DependencyGraph graph = new DependencyGraph(
                List.of(checkout.fqn(), tax.fqn(), billingCtx.fqn()),
                List.of(
                        new Edge(checkout.fqn(), tax.fqn()),
                        new Edge(checkout.fqn(), billingCtx.fqn()),
                        new Edge(tax.fqn(), billingCtx.fqn())),
                Map.of(checkout.fqn(), 2, tax.fqn(), 1, billingCtx.fqn(), 0),
                Map.of(checkout.fqn(), 0, tax.fqn(), 1, billingCtx.fqn(), 2));

        ExtractionResult result = new ExtractionResult(
                Map.of(checkout.fqn(), checkout, tax.fqn(), tax, billingCtx.fqn(), billingCtx),
                graph);

        String yaml = writer.render(result, List.of("com.sample"));

        assertTrue(yaml.contains("internal_packages:\n  - com.sample\n"));
        assertTrue(yaml.contains("com.sample.billing.CheckoutService:\n  out_degree: 2\n  dependencies:"));
        int taxPos = yaml.indexOf("fqn: com.sample.api.TaxCalculator");
        int billingPos = yaml.indexOf("fqn: com.sample.billing.BillingContext", taxPos);
        assertTrue(taxPos > 0 && billingPos > taxPos, "TaxCalculator used more than BillingContext");
        assertTrue(yaml.contains("used_by_dependent: 5"));
        assertTrue(yaml.contains("used_by_dependent: 2"));
        assertFalse(yaml.contains("String"), "third-party / unresolved types excluded");
    }

    @Test
    void writesYamlFileWhenEnabled(@TempDir Path out) throws Exception {
        Properties props = new Properties();
        props.setProperty(DependencyGraphYamlWriter.ENABLED_KEY, "true");
        props.setProperty(DependencyGraphYamlWriter.PACKAGES_KEY, "com.sample");

        ExtractionResult result = new MetadataPipeline().run(Path.of("sample-target"));
        Path written = writer.write(result, out, props);

        assertEquals("dependency-graph.yml", written.getFileName().toString());
        assertTrue(Files.exists(written));
        String content = Files.readString(written);
        assertTrue(content.contains("com.sample.billing.CheckoutService:"));
        assertTrue(content.contains("dependencies:"));
    }

    @Test
    void cliRunEmitsYamlWhenConfigured(@TempDir Path out) {
        Properties props = new Properties();
        props.setProperty(DependencyGraphYamlWriter.ENABLED_KEY, "true");
        props.setProperty(DependencyGraphYamlWriter.PACKAGES_KEY, "com.sample");

        Result ok = Cli.run(Path.of("sample-target"), out, true, props);
        assertTrue(ok.success());
        assertTrue(Files.exists(out.resolve("dependency-graph.yml")));
    }
}
