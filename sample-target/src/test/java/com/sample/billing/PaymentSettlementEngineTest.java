package com.sample.billing;

import com.sample.api.AuditLogger;
import com.sample.api.CurrencyConverter;
import com.sample.api.DiscountPolicy;
import com.sample.api.InventoryStore;
import com.sample.api.NotificationSender;
import com.sample.api.OrderRepository;
import com.sample.api.PaymentGateway;
import com.sample.api.ProductCatalog;
import com.sample.api.ShippingRateTable;
import com.sample.api.TaxCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentSettlementEngineTest {

    // M2-generated infra (Mockito only — no PowerMock). M3 adds SUT coverage tests only.

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
    private ProductCatalog productCatalog;

    @Mock
    private CurrencyConverter currencyConverter;

    @Mock
    private DiscountPolicy discountPolicy;

    @Mock
    private OrderRepository orderRepository;

    private PaymentSettlementEngine sut;

    @BeforeEach
    void setUp() {
        stubCollaborators();
        sut = buildPaymentSettlementEngine();
    }

    /** Test-data builder — 27 fields (large fixture — populate all required test data here). */
    private PaymentSettlementEngine buildPaymentSettlementEngine() {
        return new PaymentSettlementEngine(
                null,
                null,
                null,
                null,
                null,
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
        lenient().when(currencyConverter.convertToLocal(any(), anyString())).thenReturn(BigDecimal.TEN);
        lenient().when(currencyConverter.convertFromLocal(any(), anyString())).thenReturn(BigDecimal.TEN);
        lenient().when(currencyConverter.currentExchangeRate(anyString(), anyString())).thenReturn(BigDecimal.ONE);
        lenient().when(currencyConverter.isValidCurrency(anyString())).thenReturn(true);
        lenient().when(currencyConverter.isValidAmount(any())).thenReturn(true);
        lenient().when(currencyConverter.convertToLocalIfValid(any(), anyString())).thenReturn(BigDecimal.TEN);
        lenient().when(currencyConverter.convertFromLocalIfValid(any(), anyString())).thenReturn(BigDecimal.TEN);
        lenient().when(currencyConverter.safeExchangeRate(anyString(), anyString())).thenReturn(BigDecimal.ONE);
        lenient().when(currencyConverter.roundForBilling(any())).thenReturn(BigDecimal.TEN);
        lenient().when(discountPolicy.applyDiscount(any(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
        lenient().when(discountPolicy.applyTierDiscount(anyString(), any())).thenReturn(BigDecimal.TEN);
        lenient().when(discountPolicy.isPromotionActive(anyString())).thenReturn(true);
        lenient().when(discountPolicy.isValidSubtotal(any())).thenReturn(true);
        lenient().when(discountPolicy.applyDiscountIfValid(any(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
        lenient().when(discountPolicy.applyTierDiscountIfValid(anyString(), any())).thenReturn(BigDecimal.TEN);
        lenient().when(discountPolicy.isActivePromotion(anyString())).thenReturn(true);
        lenient().when(discountPolicy.discountSavings(any(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
        lenient().when(discountPolicy.effectiveSubtotal(anyString(), any())).thenReturn(BigDecimal.TEN);
        lenient().doNothing().when(orderRepository).save(any(Order.class));
        lenient().when(orderRepository.saveInvoiceDraft(any(CommercialInvoiceDraft.class))).thenReturn(false);
        lenient().when(orderRepository.findDraftByOrderId(anyString())).thenReturn(null);
        lenient().when(orderRepository.findOpenOrderIds(anyString())).thenReturn(null);
        lenient().doNothing().when(orderRepository).attachFulfillmentContext(anyString(), any(OrderFulfillmentContext.class));
        lenient().when(orderRepository.sumOutstandingForAccount(anyString())).thenReturn(BigDecimal.TEN);
        lenient().when(orderRepository.isValidOrderId(anyString())).thenReturn(true);
        lenient().when(orderRepository.isValidAccountId(anyString())).thenReturn(true);
        lenient().doNothing().when(orderRepository).saveIfPresent(any(Order.class));
        lenient().when(orderRepository.saveInvoiceDraftIfPresent(any(CommercialInvoiceDraft.class))).thenReturn(false);
        lenient().when(orderRepository.findOpenOrderIdsOrEmpty(anyString())).thenReturn(null);
        lenient().doNothing().when(orderRepository).attachFulfillmentContextIfValid(anyString(), any(OrderFulfillmentContext.class));
        lenient().when(orderRepository.safeOutstandingForAccount(anyString())).thenReturn(BigDecimal.TEN);
    }

    @Test
    void infraIsReady() {
        assertNotNull(sut);
    }

}
