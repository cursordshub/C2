package com.sample.fulfillment;

import com.sample.api.AuditLogger;
import com.sample.api.InventoryStore;
import com.sample.api.NotificationSender;
import com.sample.api.ProductCatalog;
import com.sample.api.ShippingPriority;
import com.sample.api.ShippingRateTable;
import com.sample.api.TaxCalculator;
import com.sample.billing.BillingContext;
import com.sample.billing.CommercialInvoiceDraft;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Coordinates inventory reservation, billing drafts, and shipment scheduling.
 */
public final class FulfillmentCoordinator {

    private final InventoryStore inventoryStore;
    private final ShippingRateTable shippingRateTable;
    private final NotificationSender notificationSender;
    private final AuditLogger auditLogger;
    private final ProductCatalog productCatalog;
    private final TaxCalculator taxCalculator;
    private final OrderFulfillmentContext fulfillmentContext;

    public FulfillmentCoordinator(
            InventoryStore inventoryStore,
            ShippingRateTable shippingRateTable,
            NotificationSender notificationSender,
            AuditLogger auditLogger,
            ProductCatalog productCatalog,
            TaxCalculator taxCalculator,
            OrderFulfillmentContext fulfillmentContext) {
        this.inventoryStore = inventoryStore;
        this.shippingRateTable = shippingRateTable;
        this.notificationSender = notificationSender;
        this.auditLogger = auditLogger;
        this.productCatalog = productCatalog;
        this.taxCalculator = taxCalculator;
        this.fulfillmentContext = fulfillmentContext;
    }

    public BigDecimal estimateFulfillmentCost(String sku, int quantity, ShippingPriority priority) {
        if (sku == null || sku.isBlank() || quantity <= 0) {
            throw new IllegalArgumentException("invalid sku or quantity");
        }
        if (!productCatalog.isActiveSku(sku)) {
            throw new IllegalArgumentException("inactive sku: " + sku);
        }

        BigDecimal line = productCatalog.getBulkPrice(sku, quantity);
        if (line == null || line.compareTo(BigDecimal.ZERO) <= 0) {
            line = productCatalog.getBasePrice(sku).multiply(BigDecimal.valueOf(quantity));
        }

        double weight = estimateWeight(sku, quantity);
        int distance = estimateDistance(priority);
        BigDecimal shipping = shippingRateTable.lookupRate(weight, distance);
        shipping = shipping.multiply(shippingRateTable.expressMultiplier(priority));

        if (fulfillmentContext.isPremiumChannel() && priority != ShippingPriority.STANDARD) {
            shipping = shipping.multiply(BigDecimal.valueOf(0.95));
        }

        BillingContext billing = toBillingContext(sku, quantity, priority);
        BigDecimal tax = taxCalculator.computeTax(line, billing);
        BigDecimal total = roundTotal(line.add(shipping).add(tax));
        privateAudit("estimate:" + sku + ":" + quantity);
        return total;
    }

    public boolean scheduleShipment(String sku, int quantity, ShippingPriority priority) {
        if (!canSchedule(sku, quantity)) {
            notificationSender.sendFailureAlert(fulfillmentContext.channel(), "cannot schedule " + sku);
            return false;
        }

        if (!inventoryStore.reserve(sku, quantity)) {
            auditLogger.logError("fulfillment", "reserve failed for " + sku);
            return false;
        }

        WarehouseAllocator allocator = new WarehouseAllocator();
        AllocationResult allocation = allocator.allocate(sku, quantity, priority);
        logAllocation(allocation, true);

        BillingContext billing = toBillingContext(sku, quantity, priority);
        CommercialInvoiceDraft draft = buildDraft(sku, quantity, billing, priority);
        notificationSender.sendConfirmation(billing.salesChannel, draft.invoiceNumber());
        auditLogger.logEvent("fulfillment", "scheduled " + allocation.warehouseId());
        return allocation.isComplete(quantity);
    }

    public boolean canSchedule(String sku, int quantity) {
        if (!fulfillmentContext.allowsQuantity(quantity)) {
            return false;
        }
        if (inventoryStore.availableQuantity(sku) < quantity) {
            return false;
        }
        return passesPriorityGate(priorityForQuantity(quantity), quantity);
    }

    private ShippingPriority priorityForQuantity(int quantity) {
        if (quantity >= 25) {
            return ShippingPriority.EXPRESS;
        }
        return ShippingPriority.STANDARD;
    }

    private boolean passesPriorityGate(ShippingPriority priority, int quantity) {
        if (priority == ShippingPriority.OVERNIGHT && quantity >= 100) {
            return fulfillmentContext.loyaltyTier() >= 2;
        }
        if (priority == ShippingPriority.EXPRESS && quantity >= 50) {
            return fulfillmentContext.isPremiumChannel() || fulfillmentContext.loyaltyTier() >= 1;
        }
        return true;
    }

    private double estimateWeight(String sku, int quantity) {
        double unit = helperWeight(sku);
        if (quantity >= 10 && unit >= 2.0) {
            return unit * quantity * 0.9;
        }
        return unit * quantity;
    }

    private double helperWeight(String sku) {
        if (sku.startsWith("HEAVY-")) {
            return 5.0;
        }
        if (sku.startsWith("COLD-")) {
            return 3.5;
        }
        return 1.0;
    }

    private int estimateDistance(ShippingPriority priority) {
        return switch (priority) {
            case STANDARD -> 120;
            case EXPRESS -> 350;
            case OVERNIGHT -> 500;
            case SAME_DAY -> 600;
        };
    }

    private BigDecimal roundTotal(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private void privateAudit(String message) {
        auditLogger.logEvent("fulfillment-audit", message);
    }

    private void logAllocation(AllocationResult allocation, boolean success) {
        String detail = allocation.warehouseId() + ":" + allocation.allocatedQuantity();
        if (success) {
            auditLogger.logEvent("allocation", detail);
        } else {
            auditLogger.logError("allocation", detail);
        }
    }

    private BillingContext toBillingContext(String sku, int quantity, ShippingPriority priority) {
        BigDecimal subtotal = productCatalog.getBasePrice(sku).multiply(BigDecimal.valueOf(quantity));
        return new BillingContext(
                fulfillmentContext.regionCode(),
                fulfillmentContext.currencyCode,
                quantity,
                subtotal.multiply(BigDecimal.valueOf(100)).intValue(),
                priority != ShippingPriority.STANDARD,
                fulfillmentContext.channel());
    }

    private CommercialInvoiceDraft buildDraft(
            String sku, int quantity, BillingContext billing, ShippingPriority priority) {
        BigDecimal subtotal = estimateFulfillmentCost(sku, quantity, priority);
        BigDecimal tax = taxCalculator.computeTax(subtotal, billing);
        BigDecimal shipping = shippingRateTable.lookupRate(estimateWeight(sku, quantity), estimateDistance(priority));
        return new CommercialInvoiceDraft(
                "INV-DRAFT-" + sku,
                billing.salesChannel,
                "Sample Fulfillment LLC",
                quantity,
                subtotal,
                tax,
                shipping,
                BigDecimal.ZERO,
                billing.currencyCode,
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                fulfillmentContext.fulfillmentNotes());
    }
}
