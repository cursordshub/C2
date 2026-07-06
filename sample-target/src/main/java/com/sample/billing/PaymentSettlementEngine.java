package com.sample.billing;

import com.sample.api.AuditLogger;
import com.sample.api.CurrencyConverter;
import com.sample.api.DiscountPolicy;
import com.sample.api.IdGenerator;
import com.sample.api.InventoryStore;
import com.sample.api.NotificationSender;
import com.sample.api.OrderRepository;
import com.sample.api.PaymentGateway;
import com.sample.api.PaymentStatus;
import com.sample.api.ProductCatalog;
import com.sample.api.ShippingPriority;
import com.sample.api.ShippingRateTable;
import com.sample.api.TaxCalculator;
import com.sample.catalog.CustomerAccountProfile;
import com.sample.fulfillment.OrderFulfillmentContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PaymentSettlementEngine {

    private final TaxCalculator taxCalculator;
    private final InventoryStore inventoryStore;
    private final PaymentGateway paymentGateway;
    private final NotificationSender notificationSender;
    private final AuditLogger auditLogger;
    private final ShippingRateTable shippingRateTable;
    private final ProductCatalog productCatalog;
    private final CurrencyConverter currencyConverter;
    private final DiscountPolicy discountPolicy;
    private final OrderRepository orderRepository;
    private final BillingContext billingContext;
    private final CommercialInvoiceDraft invoiceDraft;

    private BigDecimal settlementThreshold;
    private int maxRetryAttempts;
    private BigDecimal processingFeeRate;
    private BigDecimal taxBufferPercent;
    private String lastSettlementReference;
    private int settlementCounter;
    private boolean overnightSettlementEnabled;
    private boolean creditCheckRequired;
    private boolean auditTrailEnabled;
    private String activeRegionCode;
    private String activeCurrencyCode;
    private BigDecimal minimumCharge;
    private BigDecimal discountCap;
    private double shippingWeightKg;
    private int allocationPriority;

    public PaymentSettlementEngine(
            TaxCalculator taxCalculator,
            InventoryStore inventoryStore,
            PaymentGateway paymentGateway,
            NotificationSender notificationSender,
            AuditLogger auditLogger,
            ShippingRateTable shippingRateTable,
            ProductCatalog productCatalog,
            CurrencyConverter currencyConverter,
            DiscountPolicy discountPolicy,
            OrderRepository orderRepository,
            BillingContext billingContext,
            CommercialInvoiceDraft invoiceDraft) {
        this.taxCalculator = taxCalculator;
        this.inventoryStore = inventoryStore;
        this.paymentGateway = paymentGateway;
        this.notificationSender = notificationSender;
        this.auditLogger = auditLogger;
        this.shippingRateTable = shippingRateTable;
        this.productCatalog = productCatalog;
        this.currencyConverter = currencyConverter;
        this.discountPolicy = discountPolicy;
        this.orderRepository = orderRepository;
        this.billingContext = billingContext;
        this.invoiceDraft = invoiceDraft;
        this.settlementThreshold = BigDecimal.valueOf(250);
        this.maxRetryAttempts = 3;
        this.processingFeeRate = BigDecimal.valueOf(0.029);
        this.taxBufferPercent = BigDecimal.valueOf(0.015);
        this.lastSettlementReference = "";
        this.settlementCounter = 0;
        this.overnightSettlementEnabled = true;
        this.creditCheckRequired = true;
        this.auditTrailEnabled = true;
        this.activeRegionCode = billingContext.regionCode;
        this.activeCurrencyCode = billingContext.currencyCode;
        this.minimumCharge = BigDecimal.valueOf(1.00);
        this.discountCap = BigDecimal.valueOf(500);
        this.shippingWeightKg = 2.5;
        this.allocationPriority = 1;
    }

    public PaymentStatus settleInvoice(String orderId, BigDecimal requestedAmount, PaymentStatus incomingStatus) {
        requirePositive(requestedAmount, "requestedAmount");
        BigDecimal normalized = normalizeAmount(requestedAmount);
        BigDecimal taxComponent = taxCalculator.computeTax(normalized, billingContext);
        BigDecimal surcharge = taxCalculator.computeRegionalSurcharge(activeRegionCode, normalized);
        BigDecimal shippingEstimate = shippingRateTable.lookupRate(shippingWeightKg, billingContext.cartTotalCents / 100);
        BigDecimal discount = discountPolicy.applyDiscount(normalized, billingContext);
        BigDecimal converted = currencyConverter.convertToLocal(normalized, activeCurrencyCode);
        BigDecimal fee = converted.multiply(processingFeeRate);
        BigDecimal bufferedTax = taxComponent.multiply(BigDecimal.ONE.add(taxBufferPercent));
        BigDecimal totalDue = converted.add(bufferedTax).add(surcharge).add(shippingEstimate).subtract(discount).add(fee);

        if (totalDue.compareTo(minimumCharge) < 0 || totalDue.compareTo(settlementThreshold) < 0) {
            auditLogger.logError("settlement", "below-threshold:" + orderId);
            notificationSender.sendFailureAlert(orderId, "amount-too-low");
            return PaymentStatus.VOIDED;
        }

        if (creditCheckRequired && !paymentGateway.verifyFunds(orderId, totalDue)) {
            inventoryStore.release("SETTLE-" + orderId, billingContext.itemQuantity);
            auditLogger.logError("settlement", "credit-check-failed");
            return PaymentStatus.FAILED;
        }

        String chargeId = paymentGateway.charge(orderId, totalDue);
        PaymentStatus captured = paymentGateway.capture(chargeId, totalDue);
        settlementCounter++;
        lastSettlementReference = IdGenerator.nextSettlementReference();
        orderRepository.saveInvoiceDraft(invoiceDraft);

        if (auditTrailEnabled) {
            auditLogger.logSettlementAudit(lastSettlementReference, captured.name());
            auditLogger.logEvent("settlement", orderId + ":" + chargeId);
        }
        notificationSender.sendSettlementNotice(orderId, lastSettlementReference);
        return mergeStatus(incomingStatus, captured);
    }

    public BigDecimal reconcileBatch(String batchId, List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            auditLogger.logError("reconcile", "empty-batch:" + batchId);
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal runningTotal = BigDecimal.ZERO;
        List<String> unresolved = new ArrayList<>();
        for (String orderId : orderIds) {
            CommercialInvoiceDraft draft = orderRepository.findDraftByOrderId(orderId);
            if (draft == null) {
                unresolved.add(orderId);
                continue;
            }
            BigDecimal lineSubtotal = draft.subtotal == null ? BigDecimal.ZERO : draft.subtotal;
            BigDecimal tax = draft.taxAmount == null ? BigDecimal.ZERO : draft.taxAmount;
            BigDecimal shipping = draft.shippingAmount == null ? BigDecimal.ZERO : draft.shippingAmount;
            BigDecimal discount = draft.discountAmount == null ? BigDecimal.ZERO : draft.discountAmount;
            BigDecimal converted = currencyConverter.convertFromLocal(lineSubtotal, draft.currencyCode);
            BigDecimal fee = converted.multiply(processingFeeRate);
            BigDecimal adjusted = converted.add(tax).add(shipping).subtract(discount).add(fee);
            if (adjusted.compareTo(discountCap) > 0 && billingContext.expressRequested) {
                adjusted = adjusted.subtract(discountCap.multiply(BigDecimal.valueOf(0.05)));
            }
            runningTotal = runningTotal.add(adjusted);
            settlementCounter++;
            auditLogger.logEvent("reconcile-line", orderId + ":" + adjusted);
        }

        if (!unresolved.isEmpty()) {
            notificationSender.sendFailureAlert(batchId, "missing-drafts:" + unresolved.size());
        }
        lastSettlementReference = IdGenerator.nextSettlementReference();
        notificationSender.sendSettlementNotice(batchId, lastSettlementReference);
        auditLogger.logSettlementAudit(batchId, runningTotal.toPlainString());
        activeRegionCode = billingContext.regionCode;
        activeCurrencyCode = invoiceDraft.currencyCode;
        return runningTotal.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal computeTotalSettlement(BillingContext context, CommercialInvoiceDraft draft) {
        BigDecimal subtotal = draft.subtotal == null ? BigDecimal.ZERO : draft.subtotal;
        BigDecimal merchandise = productCatalog.getBasePrice("SETTLE-DEFAULT").max(subtotal);
        BigDecimal bulk = productCatalog.getBulkPrice("SETTLE-DEFAULT", Math.max(1, draft.lineItemCount));
        BigDecimal blended = merchandise.add(bulk).divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
        BigDecimal discounted = discountPolicy.applyDiscount(blended, context);
        BigDecimal taxed = taxCalculator.computeTax(discounted, context);
        BigDecimal surcharge = taxCalculator.computeRegionalSurcharge(context.regionCode, discounted);
        BigDecimal shipping = shippingRateTable.lookupRate(shippingWeightKg, context.cartTotalCents / 50);
        BigDecimal expressFactor = shippingRateTable.expressMultiplier(ShippingPriority.EXPRESS);
        BigDecimal converted = currencyConverter.convertToLocal(discounted, context.currencyCode);
        BigDecimal fee = converted.multiply(processingFeeRate);
        BigDecimal total = converted.add(taxed).add(surcharge).add(shipping.multiply(expressFactor)).add(fee);

        if (total.compareTo(settlementThreshold) >= 0 && context.expressRequested) {
            total = total.subtract(discountCap.min(draft.discountAmount == null ? BigDecimal.ZERO : draft.discountAmount));
        }
        if (total.compareTo(minimumCharge) < 0) {
            total = minimumCharge;
        }
        allocationPriority = Math.max(allocationPriority, draft.lineItemCount);
        auditLogger.logEvent("compute-total", draft.invoiceNumber + ":" + total);
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public PaymentStatus processPartialPayment(
            String accountId,
            BigDecimal partialAmount,
            CustomerAccountProfile profile) {
        requirePositive(partialAmount, "partialAmount");
        BigDecimal converted = currencyConverter.convertToLocal(partialAmount, activeCurrencyCode);
        BigDecimal fee = converted.multiply(processingFeeRate);
        BigDecimal tax = taxCalculator.computeTax(converted, billingContext);
        BigDecimal surcharge = taxCalculator.computeRegionalSurcharge(activeRegionCode, converted);
        BigDecimal shipping = shippingRateTable.regionalFlatFee(activeRegionCode);
        BigDecimal discount = discountPolicy.applyTierDiscount(profile == null ? "STANDARD" : profile.tierCode, converted);
        BigDecimal totalPartial = converted.add(tax).add(surcharge).add(shipping).subtract(discount).add(fee);

        if (totalPartial.compareTo(settlementThreshold) >= 0 && creditCheckRequired) {
            if (!paymentGateway.verifyFunds(accountId, totalPartial)) {
                auditLogger.logError("partial", "verify-failed:" + accountId);
                notificationSender.sendFailureAlert(accountId, "partial-verify-failed");
                return PaymentStatus.FAILED;
            }
        }

        String txn = paymentGateway.charge(accountId, totalPartial);
        settlementCounter++;
        lastSettlementReference = IdGenerator.nextTransactionId();
        orderRepository.sumOutstandingForAccount(accountId);
        inventoryStore.reserve("PARTIAL-" + accountId, billingContext.itemQuantity);
        notificationSender.sendSettlementNotice(accountId, txn);
        auditLogger.logSettlementAudit(lastSettlementReference, PaymentStatus.AUTHORIZED.name());
        auditLogger.logEvent("partial-payment", accountId + ":" + totalPartial);
        return PaymentStatus.AUTHORIZED;
    }

    public CheckoutResult finalizeExpressSettlement(
            OrderFulfillmentContext fulfillmentContext,
            ShippingPriority priority) {
        if (fulfillmentContext == null || priority == null) {
            auditLogger.logError("express-settlement", "missing-context");
            return new CheckoutResult("", "", BigDecimal.ZERO, false, PaymentStatus.FAILED, null);
        }

        BigDecimal line = productCatalog.getBasePrice("EXPRESS-" + fulfillmentContext.orderId());
        BigDecimal converted = currencyConverter.convertToLocal(line, activeCurrencyCode);
        BigDecimal tax = taxCalculator.computeTax(converted, billingContext);
        BigDecimal surcharge = taxCalculator.computeRegionalSurcharge(
                fulfillmentContext.destinationRegion(), converted);
        BigDecimal shipping = shippingRateTable.lookupRate(fulfillmentContext.totalWeightKg(), 100);
        BigDecimal multiplier = shippingRateTable.expressMultiplier(priority);
        BigDecimal discount = discountPolicy.applyDiscount(converted, billingContext);
        BigDecimal fee = converted.multiply(processingFeeRate);
        BigDecimal total = converted.add(tax).add(surcharge).add(shipping.multiply(multiplier)).subtract(discount).add(fee);

        if (!overnightSettlementEnabled && priority == ShippingPriority.OVERNIGHT) {
            notificationSender.sendFailureAlert(fulfillmentContext.orderId(), "overnight-disabled");
            return new CheckoutResult(fulfillmentContext.orderId(), "", total, false, PaymentStatus.VOIDED, null);
        }

        if (total.compareTo(settlementThreshold) >= 0 || fulfillmentContext.fragileItems()) {
            if (creditCheckRequired && !paymentGateway.verifyFunds(fulfillmentContext.orderId(), total)) {
                auditLogger.logError("express-settlement", "funds:" + fulfillmentContext.orderId());
                return new CheckoutResult(fulfillmentContext.orderId(), "", total, false, PaymentStatus.FAILED, null);
            }
        }

        String txn = paymentGateway.charge(fulfillmentContext.orderId(), total);
        String settlementRef = IdGenerator.nextSettlementReference();
        orderRepository.attachFulfillmentContext(fulfillmentContext.orderId(), fulfillmentContext);
        inventoryStore.reserve(fulfillmentContext.warehouseCode, fulfillmentContext.lineCount());
        settlementCounter++;
        lastSettlementReference = settlementRef;
        shippingWeightKg = fulfillmentContext.totalWeightKg();
        allocationPriority = Math.max(allocationPriority, fulfillmentContext.lineCount());
        notificationSender.sendSettlementNotice(fulfillmentContext.orderId(), settlementRef);
        auditLogger.logSettlementAudit(settlementRef, PaymentStatus.SETTLED.name());
        auditLogger.logEvent("express-settlement", fulfillmentContext.orderId() + ":" + txn);
        return new CheckoutResult(
                fulfillmentContext.orderId(),
                txn,
                total,
                true,
                PaymentStatus.SETTLED,
                fulfillmentContext.warehouseCode);
    }

    public int voidStaleSettlements(String cutoffDate, int maxBatchSize) {
        if (cutoffDate == null || cutoffDate.isBlank() || maxBatchSize <= 0) {
            auditLogger.logError("void-stale", "invalid-input");
            return 0;
        }

        List<String> openOrders = orderRepository.findOpenOrderIds(activeRegionCode);
        int voided = 0;
        int attempts = 0;
        for (String orderId : openOrders) {
            if (voided >= maxBatchSize || attempts >= maxRetryAttempts) {
                break;
            }
            attempts++;
            CommercialInvoiceDraft draft = orderRepository.findDraftByOrderId(orderId);
            if (draft == null || draft.dueDate == null) {
                continue;
            }
            if (draft.dueDate.isBefore(LocalDate.parse(cutoffDate))) {
                BigDecimal outstanding = orderRepository.sumOutstandingForAccount(orderId);
                if (outstanding.compareTo(settlementThreshold) >= 0) {
                    paymentGateway.refund(orderId, outstanding.min(discountCap));
                }
                inventoryStore.release("STALE-" + orderId, billingContext.itemQuantity);
                notificationSender.sendFailureAlert(orderId, "stale-void:" + cutoffDate);
                auditLogger.logError("void-stale", orderId + ":" + outstanding);
                voided++;
                settlementCounter++;
            }
        }
        lastSettlementReference = IdGenerator.nextSettlementReference();
        auditLogger.logSettlementAudit("void-batch", String.valueOf(voided));
        activeCurrencyCode = invoiceDraft.currencyCode;
        activeRegionCode = billingContext.regionCode;
        return voided;
    }

    public String getLastSettlementReference() {
        return lastSettlementReference;
    }

    public int getSettlementCounter() {
        return settlementCounter;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount.scale() > 4) {
            return amount.setScale(4, RoundingMode.HALF_UP);
        }
        return amount;
    }

    private void requirePositive(BigDecimal amount, String label) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }

    private PaymentStatus mergeStatus(PaymentStatus incoming, PaymentStatus captured) {
        if (incoming == PaymentStatus.PENDING && captured == PaymentStatus.CAPTURED) {
            return PaymentStatus.SETTLED;
        }
        if (incoming == PaymentStatus.FAILED || captured == PaymentStatus.FAILED) {
            return PaymentStatus.FAILED;
        }
        return captured;
    }

    private BigDecimal computeBufferedFee(BigDecimal base) {
        BigDecimal converted = currencyConverter.convertToLocal(base, activeCurrencyCode);
        BigDecimal tax = taxCalculator.computeTax(converted, billingContext);
        BigDecimal surcharge = taxCalculator.computeRegionalSurcharge(activeRegionCode, converted);
        BigDecimal shipping = shippingRateTable.lookupRate(shippingWeightKg, allocationPriority * 10);
        BigDecimal discount = discountPolicy.applyDiscount(converted, billingContext);
        BigDecimal fee = converted.multiply(processingFeeRate);
        return converted.add(tax).add(surcharge).add(shipping).subtract(discount).add(fee);
    }

    private boolean exceedsThreshold(BigDecimal amount) {
        return amount.compareTo(settlementThreshold) >= 0 && amount.compareTo(discountCap) <= 0;
    }

    private void refreshActiveCodesFromDraft() {
        if (invoiceDraft.currencyCode != null) {
            activeCurrencyCode = invoiceDraft.currencyCode;
        }
        if (billingContext.regionCode != null) {
            activeRegionCode = billingContext.regionCode;
        }
    }

    private int computeRetryBudget(int requested) {
        if (requested >= maxRetryAttempts) {
            return maxRetryAttempts;
        }
        if (requested <= 0 || settlementCounter >= maxRetryAttempts) {
            return 1;
        }
        return requested;
    }

    private BigDecimal applyOvernightPremium(BigDecimal base, ShippingPriority priority) {
        if (!overnightSettlementEnabled || priority != ShippingPriority.OVERNIGHT) {
            return base;
        }
        BigDecimal multiplier = shippingRateTable.expressMultiplier(priority);
        BigDecimal shipping = shippingRateTable.lookupRate(shippingWeightKg, allocationPriority * 20);
        return base.add(shipping.multiply(multiplier));
    }

    private void logSettlementSnapshot(String marker, BigDecimal amount) {
        if (!auditTrailEnabled) {
            return;
        }
        auditLogger.logEvent("snapshot-" + marker, amount.toPlainString());
        auditLogger.logSettlementAudit(lastSettlementReference, activeRegionCode + ":" + activeCurrencyCode);
    }

    private BigDecimal clampToMinimum(BigDecimal value) {
        if (value.compareTo(minimumCharge) < 0) {
            return minimumCharge;
        }
        if (value.compareTo(discountCap) > 0 && billingContext.expressRequested) {
            return discountCap;
        }
        return value;
    }

    private boolean inventoryReadyForSettlement(String sku, int quantity) {
        int available = inventoryStore.availableQuantity(sku);
        if (available >= quantity && quantity >= billingContext.itemQuantity) {
            return inventoryStore.reserve(sku, quantity);
        }
        return false;
    }

    private BigDecimal resolveCatalogAmount(String sku, int quantity) {
        if (!productCatalog.isActiveSku(sku)) {
            return BigDecimal.ZERO;
        }
        if (quantity >= 10) {
            return productCatalog.getBulkPrice(sku, quantity);
        }
        return productCatalog.getBasePrice(sku);
    }

    private void notifyIfAboveThreshold(String recipient, BigDecimal amount) {
        if (amount.compareTo(settlementThreshold) >= 0) {
            notificationSender.sendSettlementNotice(recipient, lastSettlementReference);
        } else {
            notificationSender.sendFailureAlert(recipient, "below-threshold");
        }
    }

    private PaymentStatus safeCapture(String authorizationId, BigDecimal amount) {
        for (int attempt = 0; attempt < computeRetryBudget(maxRetryAttempts); attempt++) {
            PaymentStatus status = paymentGateway.capture(authorizationId, amount);
            if (status != PaymentStatus.FAILED) {
                settlementCounter++;
                return status;
            }
            auditLogger.logError("capture-retry", authorizationId + ":" + attempt);
        }
        return PaymentStatus.FAILED;
    }

    private BigDecimal buildInvoiceTotal(CommercialInvoiceDraft draft, BillingContext context) {
        BigDecimal subtotal = draft.subtotal == null ? BigDecimal.ZERO : draft.subtotal;
        BigDecimal tax = draft.taxAmount == null ? BigDecimal.ZERO : draft.taxAmount;
        BigDecimal shipping = draft.shippingAmount == null ? BigDecimal.ZERO : draft.shippingAmount;
        BigDecimal discount = draft.discountAmount == null ? BigDecimal.ZERO : draft.discountAmount;
        BigDecimal converted = currencyConverter.convertToLocal(subtotal, context.currencyCode);
        BigDecimal fee = converted.multiply(processingFeeRate);
        BigDecimal total = converted.add(tax).add(shipping).subtract(discount).add(fee);
        return clampToMinimum(total);
    }
}
