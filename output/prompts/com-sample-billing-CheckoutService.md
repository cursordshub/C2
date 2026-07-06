# Coverage prompt: com.sample.billing.CheckoutService

## Task

Complete the **M3 coverage tests** for `com.sample.billing.CheckoutService`. The M2 skeleton already wires mocks, fixtures, and helpers — **do not remove or rewrite infra**. Replace `_m3Coverage` scaffold bodies and add assertions that kill surviving mutants.

## Constraints

- **Mockito only** — never PowerMock.
- Keep existing `@Mock` fields, `build*()` helpers, `stubCollaborators()`, reflection/static helpers.
- Add real assertions (`assertEquals`, `assertThrows`, `verify`, etc.) — not `assertNotNull(sut)` alone.
- Target branches listed below; prefer `@ParameterizedTest` when a scaffold already exists.
- Output: return the **full updated test class** ready to paste over `src/test/java/com/sample/billing/CheckoutServiceTest.java`.

## Class under test

- **FQN:** `com.sample.billing.CheckoutService`
- **Kind:** `CLASS`
- **Internal dependencies:** `AuditLogger`, `BigDecimal`, `BillingContext`, `CheckoutResult`, `InventoryStore`, `LegacyAccount`, `NotificationSender`, `PaymentGateway`, `PricingEngine`, `ShippingPriority`, `ShippingRateTable`, `String`, `TaxCalculator`, `WarehouseAllocator`

### Public methods (from M1 metadata)

- `calculateShippingFee(double, int, ShippingPriority)` -> `BigDecimal`
- `completeCheckout(String, int, double, int)` -> `CheckoutResult`
- `previewOrderTotal(String, int, double, int)` -> `BigDecimal`
- `canProceedWithExpressCheckout()` -> `boolean`
- `estimateDutyForSku(String, BigDecimal)` -> `BigDecimal`
- `isRegionSupported()` -> `boolean`
- `resolvePriority()` -> `ShippingPriority`

## Coverage gaps (JaCoCo / PIT / M2 scaffolds)

### `calculateShippingFee`
- Missed branches: 0
- Missed lines: 0
- Sources: [SCAFFOLD]
- Scaffold method: `calculateShippingFee_m3Coverage()`

### `completeCheckout`
- Missed branches: 0
- Missed lines: 0
- Sources: [SCAFFOLD]
- Scaffold method: `completeCheckout_m3Coverage()`

## Existing M2 test skeleton

```java
package com.sample.billing;

import com.sample.api.AuditLogger;
import com.sample.api.IdGenerator;
import com.sample.api.InventoryStore;
import com.sample.api.LegacyAccount;
import com.sample.api.NotificationSender;
import com.sample.api.PaymentGateway;
import com.sample.api.ShippingPriority;
import com.sample.api.ShippingRateTable;
import com.sample.api.TaxCalculator;
import java.lang.reflect.Field;
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
class CheckoutServiceTest {

    // M2-generated infra (Mockito only — no PowerMock). M3 adds SUT coverage tests only.
    // Target project needs: junit-jupiter + junit-jupiter-params + mockito on test classpath.

    @Mock
    private TaxCalculator taxCalculator;

    @Mock
    private InventoryStore inventoryStore;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private NotificationSender notificationSender;

    @Mock
    private AuditLogger auditLogger;

    @Mock
    private ShippingRateTable shippingRateTable;

    @Mock
    private PricingEngine pricingEngine;

    private BillingContext billingContext;

    private LegacyAccount legacyAccount;

    private CheckoutService sut;

    @BeforeEach
    void setUp() throws Exception {
        billingContext = buildBillingContext();
        legacyAccount = buildLegacyAccount();
        stubLegacyAccountHiddenState(legacyAccount);
        stubCollaborators();
        sut = new CheckoutService(taxCalculator, inventoryStore, paymentGateway, notificationSender, auditLogger, shippingRateTable, pricingEngine, billingContext, legacyAccount);
    }

    /** Test-data builder — 6 fields (medium fixture). */
    private BillingContext buildBillingContext() {
        return new BillingContext(
                "US-CA",
                "USD",
                3,
                15_000,
                true,
                "MOBILE"
        );
    }

    /** Test-data builder — 5 fields (medium fixture). */
    private LegacyAccount buildLegacyAccount() {
        return new LegacyAccount(
                "ACC-TEST-1",
                850,
                "legacy-token-ok"
        );
    }

    /** Stub hidden legacy state — package-private + reflection (not @Mock). */
    private void stubLegacyAccountHiddenState(LegacyAccount account) throws Exception {
        account.legacyToken = "stub-token";
        var credit = LegacyAccount.class.getDeclaredField("internalCreditScore");
        credit.setAccessible(true);
        credit.setInt(account, 900);
    }

    /** Lenient Mockito stubs — collaborators pre-wired for SUT tests. */
    private void stubCollaborators() {
        setupApiMocks();
        setupBillingMocks();
    }

    private void setupApiMocks() {
        lenient().when(taxCalculator.computeTax(any(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.computeRegionalSurcharge(anyString(), any())).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.computeImportDuty(anyString(), any())).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.isRegionTaxExempt(anyString())).thenReturn(true);
        lenient().when(taxCalculator.isValidTaxableAmount(any())).thenReturn(true);
        lenient().when(taxCalculator.computeTaxIfValid(any(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.computeRegionalSurchargeIfValid(anyString(), any())).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.computeImportDutyIfValid(anyString(), any())).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.totalTaxWithSurcharge(any(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
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
        lenient().when(paymentGateway.charge(anyString(), any())).thenReturn("stub-id");
        lenient().when(paymentGateway.verifyFunds(anyString(), any())).thenReturn(true);
        lenient().when(paymentGateway.capture(anyString(), any())).thenReturn(null);
        lenient().doNothing().when(paymentGateway).refund(anyString(), any());
        lenient().when(paymentGateway.isValidAccountToken(anyString())).thenReturn(true);
        lenient().when(paymentGateway.isChargeableAmount(any())).thenReturn(true);
        lenient().when(paymentGateway.verifyFundsIfValid(anyString(), any())).thenReturn(true);
        lenient().when(paymentGateway.chargeIfValid(anyString(), any())).thenReturn("stub-id");
        lenient().when(paymentGateway.captureIfValid(anyString(), any())).thenReturn(null);
        lenient().doNothing().when(paymentGateway).refundIfValid(anyString(), any());
        lenient().when(paymentGateway.normalizeChargeAmount(any())).thenReturn(BigDecimal.TEN);
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
    }

    private void setupBillingMocks() {
        lenient().when(pricingEngine.quoteLineTotal(anyString(), anyInt(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
        lenient().when(pricingEngine.quoteWithFallback(anyString(), anyInt(), any(BillingContext.class), anyString())).thenReturn(BigDecimal.TEN);
        lenient().when(pricingEngine.quotePremiumBundle(anyString(), anyInt(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
        lenient().when(pricingEngine.estimateTaxableSubtotal(anyString(), anyInt(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
        lenient().when(pricingEngine.quoteCategoryAdjustedTotal(anyString(), anyInt(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
        lenient().when(pricingEngine.isSkuQuotable(anyString(), any(BillingContext.class))).thenReturn(true);
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
    void calculateShippingFee_m3Coverage(double doubleValue, int intValue, ShippingPriority shippingPriority) {
        // M3: assert expected outcomes for calculateShippingFee
        assertNotNull(sut.calculateShippingFee(doubleValue, intValue, shippingPriority));
    }

    @Test
    void completeCheckout_m3Coverage() {
        withStaticIds(() -> {
            // M3: exercise completeCheckout — WarehouseAllocator is not injectable
            assertNotNull(sut);
        });
    }

}

```
