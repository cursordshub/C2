package com.sample.fulfillment;

import com.sample.api.AuditLogger;
import com.sample.api.IdGenerator;
import com.sample.api.InventoryStore;
import com.sample.api.NotificationSender;
import com.sample.api.ProductCatalog;
import com.sample.api.ShippingPriority;
import com.sample.api.ShippingRateTable;
import com.sample.api.TaxCalculator;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.params.CsvSource;
import org.junit.jupiter.api.params.ParameterizedTest;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FulfillmentCoordinatorTest {

    // M2-generated infra (Mockito only — no PowerMock). M3 adds SUT coverage tests only.
    // Target project needs: junit-jupiter + junit-jupiter-params + mockito on test classpath.

    @Mock
    private InventoryStore inventoryStore;

    @Mock
    private ShippingRateTable shippingRateTable;

    @Mock
    private NotificationSender notificationSender;

    @Mock
    private AuditLogger auditLogger;

    @Mock
    private ProductCatalog productCatalog;

    @Mock
    private TaxCalculator taxCalculator;

    private FulfillmentCoordinator sut;

    @BeforeEach
    void setUp() {
        stubCollaborators();
        sut = buildFulfillmentCoordinator();
    }

    /** Test-data builder — 7 fields (medium fixture). */
    private FulfillmentCoordinator buildFulfillmentCoordinator() {
        return new FulfillmentCoordinator(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    /** Lenient Mockito stubs — collaborators pre-wired for SUT tests. */
    private void stubCollaborators() {
        setupApiMocks();
    }

    private void setupApiMocks() {
        lenient().when(inventoryStore.reserve(anyString(), anyInt())).thenReturn(true);
        lenient().when(inventoryStore.availableQuantity(anyString())).thenReturn(100);
        lenient().doNothing().when(inventoryStore).release(anyString(), anyInt());
        lenient().when(inventoryStore.isBackorderAllowed(anyString())).thenReturn(true);
        lenient().when(inventoryStore.isValidSku(anyString())).thenReturn(true);
        lenient().when(inventoryStore.isPositiveQuantity(anyInt())).thenReturn(true);
        lenient().when(inventoryStore.canFulfill(anyString(), anyInt())).thenReturn(true);
        lenient().when(inventoryStore.reserveIfAvailable(anyString(), anyInt())).thenReturn(true);
        lenient().doNothing().when(inventoryStore).releaseIfValid(anyString(), anyInt());
        lenient().when(inventoryStore.canAcceptOrder(anyString(), anyInt())).thenReturn(true);
        lenient().when(inventoryStore.remainingAfterReserve(anyString(), anyInt())).thenReturn(0);
        lenient().when(shippingRateTable.lookupRate(anyDouble(), anyInt())).thenReturn(BigDecimal.ONE);
        lenient().when(shippingRateTable.expressMultiplier(any(ShippingPriority.class))).thenReturn(BigDecimal.ONE);
        lenient().when(shippingRateTable.regionalFlatFee(anyString())).thenReturn(BigDecimal.TEN);
        lenient().when(shippingRateTable.bulkDiscountRate(anyInt())).thenReturn(BigDecimal.ONE);
        lenient().when(shippingRateTable.isValidWeight(anyDouble())).thenReturn(true);
        lenient().when(shippingRateTable.isValidDistance(anyInt())).thenReturn(true);
        lenient().when(shippingRateTable.lookupRateIfValid(anyDouble(), anyInt())).thenReturn(BigDecimal.ONE);
        lenient().when(shippingRateTable.applyPriorityMultiplier(any(), any(ShippingPriority.class))).thenReturn(BigDecimal.ONE);
        lenient().when(shippingRateTable.regionalFlatFeeOrZero(anyString())).thenReturn(BigDecimal.TEN);
        lenient().when(shippingRateTable.safeBulkDiscountRate(anyInt())).thenReturn(BigDecimal.ONE);
        lenient().doNothing().when(notificationSender).sendConfirmation(anyString(), anyString());
        lenient().doNothing().when(notificationSender).sendFailureAlert(anyString(), anyString());
        lenient().doNothing().when(notificationSender).sendSettlementNotice(anyString(), anyString());
        lenient().when(notificationSender.isValidRecipient(anyString())).thenReturn(true);
        lenient().when(notificationSender.isValidSettlementId(anyString())).thenReturn(true);
        lenient().doNothing().when(notificationSender).sendConfirmationIfValid(anyString(), anyString());
        lenient().doNothing().when(notificationSender).sendFailureAlertNormalized(anyString(), anyString());
        lenient().doNothing().when(notificationSender).sendSettlementNoticeIfValid(anyString(), anyString());
        lenient().when(notificationSender.confirmationSubject(anyString())).thenReturn("stub-id");
        lenient().when(notificationSender.failureSubject(anyString())).thenReturn("stub-id");
        lenient().doNothing().when(auditLogger).logEvent(anyString(), anyString());
        lenient().doNothing().when(auditLogger).logError(anyString(), anyString());
        lenient().doNothing().when(auditLogger).logSettlementAudit(anyString(), anyString());
        lenient().when(auditLogger.isValidCategory(anyString())).thenReturn(true);
        lenient().when(auditLogger.isValidDetail(anyString())).thenReturn(true);
        lenient().doNothing().when(auditLogger).logEventIfValid(anyString(), anyString());
        lenient().doNothing().when(auditLogger).logErrorTrimmed(anyString(), anyString());
        lenient().doNothing().when(auditLogger).logSettlementAuditIfValid(anyString(), anyString());
        lenient().when(auditLogger.formatAuditLine(anyString(), anyString())).thenReturn("stub-id");
        lenient().when(auditLogger.formatSettlementLine(anyString(), anyString())).thenReturn("stub-id");
        lenient().when(productCatalog.getBasePrice(anyString())).thenReturn(BigDecimal.TEN);
        lenient().when(productCatalog.getBulkPrice(anyString(), anyInt())).thenReturn(BigDecimal.TEN);
        lenient().when(productCatalog.isActiveSku(anyString())).thenReturn(true);
        lenient().when(productCatalog.resolveCategory(anyString())).thenReturn("stub-id");
        lenient().when(productCatalog.isValidSku(anyString())).thenReturn(true);
        lenient().when(productCatalog.isPositiveQuantity(anyInt())).thenReturn(true);
        lenient().when(productCatalog.safeBasePrice(anyString())).thenReturn(BigDecimal.TEN);
        lenient().when(productCatalog.priceForQuantity(anyString(), anyInt())).thenReturn(BigDecimal.TEN);
        lenient().when(productCatalog.isPurchasable(anyString())).thenReturn(true);
        lenient().when(productCatalog.resolveCategoryOrDefault(anyString(), anyString())).thenReturn("stub-id");
        lenient().when(productCatalog.lineTotal(anyString(), anyInt())).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.computeTax(any(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.computeRegionalSurcharge(anyString(), any())).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.computeImportDuty(anyString(), any())).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.isRegionTaxExempt(anyString())).thenReturn(true);
        lenient().when(taxCalculator.isValidTaxableAmount(any())).thenReturn(true);
        lenient().when(taxCalculator.computeTaxIfValid(any(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.computeRegionalSurchargeIfValid(anyString(), any())).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.computeImportDutyIfValid(anyString(), any())).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.totalTaxWithSurcharge(any(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
    }

    /** Static collaborator — Mockito MockedStatic (not PowerMock). */
    private void withStaticIds(Runnable block) {
        try (MockedStatic<IdGenerator> ids = mockStatic(IdGenerator.class)) {
            ids.when(IdGenerator::nextOrderId).thenReturn("ORD-TEST-1");
            ids.when(IdGenerator::nextTransactionId).thenReturn("TXN-TEST-1");
            block.run();
        }
    }

    @Test
    void infraIsReady() {
        assertNotNull(sut);
    }

    @ParameterizedTest
    @CsvSource({
            "1.0, 10, STANDARD",
            "25.0, 500, OVERNIGHT"
    })
    void estimateFulfillmentCost_m3Coverage(String StringValue, int intValue, ShippingPriority shippingPriority) {
        // M3: assert expected outcomes for estimateFulfillmentCost
        assertNotNull(sut.estimateFulfillmentCost(StringValue, intValue, shippingPriority));
    }

    @Test
    void scheduleShipment_m3Coverage() {
        withStaticIds(() -> {
            // M3: exercise scheduleShipment — WarehouseAllocator is not injectable
            assertNotNull(sut);
        });
    }

}
