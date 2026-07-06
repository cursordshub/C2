package com.sample.fulfillment;

import com.sample.api.ShippingPriority;

/**
 * Immutable dispatch request with constructor-only access.
 */
public final class WarehouseDispatchRequest {

    private final String sku;
    private final int quantity;
    private final ShippingPriority priority;
    private final String warehouseId;
    private final String destinationRegion;
    private final double weightKg;
    private final boolean fragile;

    public WarehouseDispatchRequest(
            String sku,
            int quantity,
            ShippingPriority priority,
            String warehouseId,
            String destinationRegion,
            double weightKg,
            boolean fragile) {
        this.sku = sku;
        this.quantity = quantity;
        this.priority = priority;
        this.warehouseId = warehouseId;
        this.destinationRegion = destinationRegion;
        this.weightKg = weightKg;
        this.fragile = fragile;
    }

    String sku() {
        return sku;
    }

    int quantity() {
        return quantity;
    }

    ShippingPriority priority() {
        return priority;
    }

    String warehouseId() {
        return warehouseId;
    }

    String destinationRegion() {
        return destinationRegion;
    }

    double weightKg() {
        return weightKg;
    }

    boolean fragile() {
        return fragile;
    }

    /**
     * Returns true when sku and quantity are valid for dispatch.
     */
    boolean isValidForDispatch() {
        return sku != null && !sku.isBlank() && quantity > 0 && weightKg > 0.0;
    }

    /**
     * Returns true when fragile handling is required.
     */
    boolean requiresFragileHandling() {
        return fragile;
    }

    /**
     * Returns true when an expedited shipping priority was requested.
     */
    boolean isExpedited() {
        return priority != null && priority.isExpedited();
    }

    /**
     * Returns total shipment weight assuming uniform unit weight.
     */
    double totalWeightKg() {
        return Math.max(weightKg, weightKg * Math.max(1, quantity));
    }

    /**
     * Returns a routing key combining warehouse and destination region.
     */
    String routingKey() {
        return (warehouseId == null ? "" : warehouseId) + "->" + (destinationRegion == null ? "" : destinationRegion);
    }
}
