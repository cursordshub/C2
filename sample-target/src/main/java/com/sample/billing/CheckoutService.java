package com.sample.billing;

import com.sample.api.AuditLogger;
import com.sample.api.IdGenerator;
import com.sample.api.InventoryStore;
import com.sample.api.LegacyAccount;
import com.sample.api.NotificationSender;
import com.sample.api.PaymentGateway;
import com.sample.api.PaymentStatus;
import com.sample.api.ShippingPriority;
import com.sample.api.ShippingRateTable;
import com.sample.api.TaxCalculator;
import com.sample.fulfillment.WarehouseAllocator;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class CheckoutService {

    private final TaxCalculator taxCalculator;
    private final InventoryStore inventoryStore;
    private final PaymentGateway paymentGateway;
    private final NotificationSender notificationSender;
    private final AuditLogger auditLogger;
    private final ShippingRateTable shippingRateTable;
    private final PricingEngine pricingEngine;
    private final BillingContext billingContext;
    private final LegacyAccount legacyAccount;

    public CheckoutService(
            TaxCalculator taxCalculator,
            InventoryStore inventoryStore,
            PaymentGateway paymentGateway,
            NotificationSender notificationSender,
            AuditLogger auditLogger,
            ShippingRateTable shippingRateTable,
            PricingEngine pricingEngine,
            BillingContext billingContext,
            LegacyAccount legacyAccount) {
        this.taxCalculator = taxCalculator;
        this.inventoryStore = inventoryStore;
        this.paymentGateway = paymentGateway;
        this.notificationSender = notificationSender;
        this.auditLogger = auditLogger;
        this.shippingRateTable = shippingRateTable;
        this.pricingEngine = pricingEngine;
        this.billingContext = billingContext;
        this.legacyAccount = legacyAccount;
    }

    public BigDecimal calculateShippingFee(double weightKg, int distanceKm, ShippingPriority priority) {
        BigDecimal baseRate = shippingRateTable.lookupRate(weightKg, distanceKm);
        BigDecimal multiplier = shippingRateTable.expressMultiplier(priority);
        BigDecimal regionalFee = shippingRateTable.regionalFlatFee(billingContext.regionCode);

        BigDecimal subtotal = baseRate.multiply(multiplier).add(regionalFee);
        if (weightKg >= 20.0 && distanceKm >= 300) {
            subtotal = subtotal.multiply(BigDecimal.valueOf(1.15));
        } else if (weightKg >= 5.0 || distanceKm >= 150) {
            subtotal = subtotal.multiply(BigDecimal.valueOf(1.05));
        }

        if (priority == ShippingPriority.OVERNIGHT && legacyAccount.qualifiesForExpress()) {
            subtotal = subtotal.subtract(regionalFee.multiply(BigDecimal.valueOf(0.10)));
        }

        if (billingContext.expressRequested && priority != ShippingPriority.STANDARD) {
            subtotal = subtotal.add(shippingRateTable.bulkDiscountRate(billingContext.itemQuantity).negate());
        }

        auditLogger.logEvent("shipping-fee", billingContext.regionCode + ":" + priority);
        return subtotal.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public CheckoutResult completeCheckout(String sku, int quantity, double weightKg, int distanceKm) {
        String orderId = IdGenerator.nextOrderId();
        String transactionId = IdGenerator.nextTransactionId();
        auditLogger.logEvent("checkout-start", orderId);

        if (!inventoryStore.reserve(sku, quantity)) {
            notificationSender.sendFailureAlert(legacyAccount.getAccountId(), "inventory-unavailable");
            auditLogger.logError("checkout", "reserve-failed:" + sku);
            return new CheckoutResult(orderId, transactionId, BigDecimal.ZERO, false, PaymentStatus.FAILED, null);
        }

        BigDecimal lineTotal = pricingEngine.quoteLineTotal(sku, quantity, billingContext);
        BigDecimal tax = taxCalculator.computeTax(lineTotal, billingContext);
        BigDecimal surcharge = taxCalculator.computeRegionalSurcharge(billingContext.regionCode, lineTotal);
        BigDecimal shipping = calculateShippingFee(weightKg, distanceKm, resolvePriority());
        BigDecimal grandTotal = lineTotal.add(tax).add(surcharge).add(shipping);

        if (!paymentGateway.verifyFunds(legacyAccount.getAccountId(), grandTotal)) {
            inventoryStore.release(sku, quantity);
            notificationSender.sendFailureAlert(legacyAccount.getAccountId(), "insufficient-funds");
            auditLogger.logError("checkout", "funds:" + orderId);
            return new CheckoutResult(orderId, transactionId, grandTotal, false, PaymentStatus.FAILED, null);
        }

        paymentGateway.charge(legacyAccount.getAccountId(), grandTotal);
        WarehouseAllocator allocator = new WarehouseAllocator();
        String warehouse = allocator.allocatePrimaryWarehouse(billingContext.regionCode, quantity);
        int pickMinutes = allocator.estimatePickMinutes(warehouse, quantity);

        notificationSender.sendConfirmation(legacyAccount.getAccountId(), orderId + ":" + warehouse);
        auditLogger.logEvent("checkout-complete", orderId + ":pick=" + pickMinutes);

        if (grandTotal.compareTo(BigDecimal.valueOf(500)) >= 0 && billingContext.salesChannel.equals("WEB")) {
            taxCalculator.computeImportDuty(sku, lineTotal);
        }

        return new CheckoutResult(orderId, transactionId, grandTotal, true, PaymentStatus.CAPTURED, warehouse);
    }

    public BigDecimal previewOrderTotal(String sku, int quantity, double weightKg, int distanceKm) {
        BigDecimal merchandise = pricingEngine.quoteWithFallback(sku, quantity, billingContext, sku + "-ALT");
        BigDecimal tax = taxCalculator.computeTax(merchandise, billingContext);
        BigDecimal shipping = calculateShippingFee(weightKg, distanceKm, ShippingPriority.STANDARD);
        return merchandise.add(tax).add(shipping);
    }

    public boolean canProceedWithExpressCheckout() {
        return legacyAccount.qualifiesForExpress()
                && billingContext.expressRequested
                && inventoryStore.availableQuantity("EXPRESS-LANE") > 0;
    }

    public BigDecimal estimateDutyForSku(String sku, BigDecimal declaredValue) {
        if (declaredValue == null || declaredValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (billingContext.regionCode == null || billingContext.regionCode.isBlank()) {
            return BigDecimal.ZERO;
        }
        BigDecimal duty = taxCalculator.computeImportDuty(sku, declaredValue);
        BigDecimal surcharge = taxCalculator.computeRegionalSurcharge(billingContext.regionCode, duty);
        return duty.add(surcharge).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isRegionSupported() {
        return billingContext.regionCode != null
                && !billingContext.regionCode.isBlank()
                && !taxCalculator.isRegionTaxExempt(billingContext.regionCode);
    }

    private ShippingPriority resolvePriority() {
        if (billingContext.expressRequested) {
            if (legacyAccount.qualifiesForExpress()) {
                return ShippingPriority.OVERNIGHT;
            }
            return ShippingPriority.EXPRESS;
        }
        return ShippingPriority.STANDARD;
    }
}
