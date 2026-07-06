package com.sample.fulfillment;

import com.sample.api.ShippingPriority;

/**
 * Value type produced by {@link WarehouseAllocator}.
 */
public final class AllocationResult {

    private final String warehouseId;
    private final String sku;
    private final int allocatedQuantity;
    private final int leadDays;
    private final ShippingPriority priority;
    private final boolean partial;

    AllocationResult(
            String warehouseId,
            String sku,
            int allocatedQuantity,
            int leadDays,
            ShippingPriority priority,
            boolean partial) {
        this.warehouseId = warehouseId;
        this.sku = sku;
        this.allocatedQuantity = allocatedQuantity;
        this.leadDays = leadDays;
        this.priority = priority;
        this.partial = partial;
    }

    public String warehouseId() {
        return warehouseId;
    }

    public String sku() {
        return sku;
    }

    public int allocatedQuantity() {
        return allocatedQuantity;
    }

    public int leadDays() {
        return leadDays;
    }

    public ShippingPriority priority() {
        return priority;
    }

    public boolean partial() {
        return partial;
    }

    public boolean isComplete(int requestedQuantity) {
        return !partial && allocatedQuantity >= requestedQuantity;
    }

    /**
     * Returns true when a warehouse was assigned.
     */
    public boolean hasWarehouse() {
        return warehouseId != null && !warehouseId.isBlank();
    }

    /**
     * Returns true when any quantity was allocated.
     */
    public boolean hasAllocation() {
        return allocatedQuantity > 0;
    }

    /**
     * Returns the unfulfilled quantity for a request.
     */
    public int shortfall(int requestedQuantity) {
        if (requestedQuantity <= 0) {
            return 0;
        }
        return Math.max(0, requestedQuantity - allocatedQuantity);
    }

    /**
     * Returns true when the allocation used an expedited priority.
     */
    public boolean isExpedited() {
        return priority != null && priority.isExpedited();
    }

    /**
     * Returns a diagnostic label for fulfillment logs.
     */
    public String diagnosticLabel() {
        return warehouseId + ":" + sku + ":qty=" + allocatedQuantity + ":partial=" + partial;
    }
}
