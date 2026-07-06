package com.toolchain.testgen;

import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.Model.ClassType;
import com.toolchain.metadata.Model.ConstructorInfo;
import com.toolchain.metadata.Model.ExtractionResult;
import com.toolchain.testgen.TestGenModel.DepDecision;
import com.toolchain.testgen.TestGenModel.Skeleton;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the real SkeletonBuilder + mock policy. (Scaffolding until STATE 3.) */
class SkeletonBuilderTest {

    private static ClassMetadata cls(String fqn, ClassType type, ConstructorInfo... ctors) {
        return new ClassMetadata(fqn, type, false, List.of(), Map.of(), List.of(), List.of(), List.of(ctors));
    }

    @Test
    void mocksInternalCollaboratorsAndUsesRealValueForString() {
        ClassMetadata order = cls("com.sample.catalog.Order", ClassType.CLASS,
                new ConstructorInfo(List.of("String", "Customer", "PricingService"), "public"));
        Map<String, ClassMetadata> index = Map.of(
                "com.sample.catalog.Order", order,
                "com.sample.catalog.Customer", cls("com.sample.catalog.Customer", ClassType.CLASS),
                "com.sample.catalog.PricingService", cls("com.sample.catalog.PricingService", ClassType.CLASS));

        Skeleton s = new SkeletonBuilder().build(order, index);
        String src = s.source();

        assertTrue(src.contains("package com.sample.catalog;"), "mirrors SUT package");
        assertTrue(src.contains("class OrderTest"), "test class name");
        assertTrue(src.contains("@ExtendWith(MockitoExtension.class)"), "mockito extension present when mocks exist");
        assertTrue(src.contains("private Customer customer;"), "declares mock field for Customer");
        assertTrue(src.contains("private PricingService pricingService;"), "declares mock field for PricingService");
        assertTrue(src.contains("new Order(\"\", customer, pricingService)"), "wires ctor: String real, deps mocked");
        assertTrue(src.contains("@Test"), "discoverable JUnit5 test method");
        assertTrue(src.contains("infraIsReady"), "infra smoke test");
        assertTrue(src.contains("stubCollaborators"), "mock stubs generated");

        DepDecision customer = find(s.deps(), "Customer");
        DepDecision pricing = find(s.deps(), "PricingService");
        DepDecision str = find(s.deps(), "String");
        assertTrue(customer.mock());
        assertTrue(pricing.mock());
        assertFalse(str.mock(), "String is final value type -> real, not mocked");
        assertEquals("\"\"", str.valueExpr());
    }

    @Test
    void refusesToMockPrimitivesEnumsAndInterfacesAreMocked() {
        ClassMetadata widget = cls("com.sample.Widget", ClassType.CLASS,
                new ConstructorInfo(List.of("int", "PaymentStatus", "OrderRepository"), "public"));
        Map<String, ClassMetadata> index = Map.of(
                "com.sample.Widget", widget,
                "com.sample.api.PaymentStatus", cls("com.sample.api.PaymentStatus", ClassType.ENUM),
                "com.sample.api.OrderRepository", cls("com.sample.api.OrderRepository", ClassType.INTERFACE));

        Skeleton s = new SkeletonBuilder().build(widget, index);

        assertFalse(find(s.deps(), "int").mock(), "primitive not mockable");
        assertFalse(find(s.deps(), "PaymentStatus").mock(), "enum is final -> not mockable");
        assertTrue(find(s.deps(), "OrderRepository").mock(), "interface -> mockable");
    }

    @Test
    void constructorFixtureWithManyFieldsIsRealNotMocked() {
        ClassMetadata orderContext = new ClassMetadata(
                "com.sample.fulfillment.OrderFulfillmentContext", ClassType.CLASS, false, List.of(), Map.of(),
                List.of(),
                List.of(
                        new com.toolchain.metadata.Model.FieldInfo("regionCode", "String", "private"),
                        new com.toolchain.metadata.Model.FieldInfo("currencyCode", "String", "private"),
                        new com.toolchain.metadata.Model.FieldInfo("loyaltyTier", "int", "private"),
                        new com.toolchain.metadata.Model.FieldInfo("maxQuantity", "int", "private"),
                        new com.toolchain.metadata.Model.FieldInfo("taxExempt", "boolean", "private"),
                        new com.toolchain.metadata.Model.FieldInfo("channel", "String", "private"),
                        new com.toolchain.metadata.Model.FieldInfo("warehouseCode", "String", "private"),
                        new com.toolchain.metadata.Model.FieldInfo("coldChainRequired", "boolean", "private"),
                        new com.toolchain.metadata.Model.FieldInfo("allocationPriority", "int", "private"),
                        new com.toolchain.metadata.Model.FieldInfo("carrierPreference", "String", "private"),
                        new com.toolchain.metadata.Model.FieldInfo("premiumChannel", "boolean", "private"),
                        new com.toolchain.metadata.Model.FieldInfo("fulfillmentNotes", "String", "private")),
                List.of(new ConstructorInfo(
                        List.of("String", "String", "int", "int", "boolean", "String",
                                "String", "boolean", "int", "String", "boolean", "String"), "public")));
        ClassMetadata checkout = cls("com.sample.billing.CheckoutService", ClassType.CLASS,
                new ConstructorInfo(List.of("TaxCalculator", "OrderFulfillmentContext"), "public"));
        Map<String, ClassMetadata> index = Map.of(
                "com.sample.billing.CheckoutService", checkout,
                "com.sample.fulfillment.OrderFulfillmentContext", orderContext,
                "com.sample.api.TaxCalculator", cls("com.sample.api.TaxCalculator", ClassType.INTERFACE));

        Skeleton s = new SkeletonBuilder().build(checkout, index);
        DepDecision ctx = find(s.deps(), "OrderFulfillmentContext");
        assertFalse(ctx.mock(), "OrderFulfillmentContext is a constructor fixture — not @Mock");
        assertEquals("buildOrderFulfillmentContext()", ctx.valueExpr());
        assertFalse(s.source().contains("@Mock\n    private OrderFulfillmentContext"));
        assertTrue(s.source().contains("private OrderFulfillmentContext orderFulfillmentContext"));
        assertTrue(s.source().contains("buildOrderFulfillmentContext()"));
        assertTrue(s.source().contains("large fixture"));
        assertTrue(s.source().contains("stubCollaborators"));
    }

    @Test
    void checkoutServiceFixtureIncludesReflectionStubsAndParameterizedScaffold() {
        ExtractionResult m1 = new com.toolchain.metadata.MetadataPipeline().run(Path.of("sample-target"));
        Map<String, ClassMetadata> index = new HashMap<>();
        m1.byFqn().forEach(index::put);
        ClassMetadata checkout = index.get("com.sample.billing.CheckoutService");

        Skeleton s = new SkeletonBuilder().build(checkout, index);
        String src = s.source();

        assertTrue(src.contains("buildBillingContext()"));
        assertTrue(src.contains("buildLegacyAccount()"));
        assertTrue(src.contains("medium fixture"));
        assertTrue(src.contains("stubLegacyAccountHiddenState"));
        assertTrue(src.contains("getDeclaredField(\"internalCreditScore\")"));
        assertTrue(src.contains("withStaticIds"));
        assertTrue(src.contains("MockedStatic<IdGenerator>"));
        assertTrue(src.contains("com.sample.api.IdGenerator"));
        assertTrue(src.contains("calculateShippingFee_m3Coverage"));
        assertTrue(src.contains("@CsvSource"));
        assertTrue(src.contains("stubCollaborators()"));
        assertTrue(src.contains("setupApiMocks"));
        assertTrue(src.contains("lenient()."));
        assertTrue(src.contains("no PowerMock"));
        assertFalse(src.contains("org.powermock"));
    }

    private static DepDecision find(List<DepDecision> deps, String type) {
        return deps.stream().filter(d -> d.type().equals(type)).findFirst().orElseThrow();
    }
}
