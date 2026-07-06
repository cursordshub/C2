package com.toolchain.metadata;

import com.toolchain.metadata.Model.Edge;
import com.toolchain.metadata.Model.ExtractionResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test: exercises the real {@link MetadataPipeline} walking the whole sample target,
 * including internal dependency-graph edges. (Scaffolding until STATE 3.)
 */
class MetadataPipelineTest {

    private static final Path SAMPLE = Path.of("sample-target");

    @Test
    void buildsMetadataAndInternalGraphForSampleTarget() {
        ExtractionResult result = new MetadataPipeline().run(SAMPLE);

        assertTrue(result.byFqn().containsKey("com.sample.catalog.Order"));
        assertTrue(result.byFqn().containsKey("com.sample.catalog.PricingService"));
        assertTrue(result.byFqn().containsKey("com.sample.api.OrderRepository"));

        boolean orderToCustomer = result.graph().edges().stream()
                .anyMatch(e -> e.equals(new Edge("com.sample.catalog.Order", "com.sample.catalog.Customer")));
        boolean orderToPricing = result.graph().edges().stream()
                .anyMatch(e -> e.equals(new Edge("com.sample.catalog.Order", "com.sample.catalog.PricingService")));
        assertTrue(orderToCustomer, "Order -> Customer edge (internal)");
        assertTrue(orderToPricing, "Order -> PricingService edge (internal)");

        assertTrue(result.graph().outDegree().getOrDefault("com.sample.catalog.Order", 0) >= 2,
                "Order has outdegree >= 2");
    }

    @Test
    void emptyOrMissingTargetFailsLoudly() {
        assertThrows(RuntimeException.class, () -> new MetadataPipeline().run(Path.of("does-not-exist-xyz")),
                "F1: missing target must throw, not silently succeed");
    }
}
