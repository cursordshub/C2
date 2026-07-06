package com.toolchain.metadata;

import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.Model.ClassType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test: exercises the real {@link SourceScanner} against the bundled sample target.
 * (Scaffolding until STATE 3 fills the implementation.)
 */
class SourceScannerTest {

    private static final Path SAMPLE = Path.of("sample-target", "src", "main", "java", "com", "sample", "catalog");

    @Test
    void scansConcreteClassSignaturesConstructorsAndDependencies() {
        List<ClassMetadata> types = new SourceScanner().scan(SAMPLE.resolve("Order.java"));
        assertEquals(1, types.size(), "Order.java declares one top-level type");

        ClassMetadata order = types.get(0);
        assertEquals("com.sample.catalog.Order", order.fqn());
        assertEquals(ClassType.CLASS, order.classType());
        assertEquals(1, order.constructors().size());
        assertEquals(3, order.constructors().get(0).parameterTypes().size(),
                "Order(String, Customer, PricingService)");

        assertTrue(order.dependencies().contains("Customer"), "field/param dep on Customer");
        assertTrue(order.dependencies().contains("PricingService"), "field/param dep on PricingService");

        assertNotNull(order.methods());
        assertTrue(order.methods().stream().anyMatch(m -> m.name().equals("total")),
                "public double total(int) present");
    }

    @Test
    void detectsClassKindAndStaticInitializer() {
        ClassMetadata iface = new SourceScanner()
                .scan(Path.of("sample-target", "src", "main", "java", "com", "sample", "api", "OrderRepository.java"))
                .get(0);
        assertEquals(ClassType.INTERFACE, iface.classType());

        ClassMetadata en = new SourceScanner().scan(SAMPLE.resolve("PaymentStatus.java")).get(0);
        assertEquals(ClassType.ENUM, en.classType());

        ClassMetadata pricing = new SourceScanner().scan(SAMPLE.resolve("PricingService.java")).get(0);
        assertTrue(pricing.hasStaticInitializer(), "PricingService has a static { } block");
    }
}
