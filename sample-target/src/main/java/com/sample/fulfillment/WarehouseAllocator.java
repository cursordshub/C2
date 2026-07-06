package com.sample.fulfillment;

import com.sample.api.ShippingPriority;

/**
 * Resolves warehouse capacity and builds {@link AllocationResult} internally.
 */
public final class WarehouseAllocator {

    private static final int DEFAULT_LEAD_DAYS = 3;
    private static final int EXPRESS_LEAD_DAYS = 1;
    private static final int OVERNIGHT_LEAD_DAYS = 0;

    public AllocationResult allocate(String sku, int quantity) {
        return allocate(sku, quantity, ShippingPriority.STANDARD);
    }

    public AllocationResult allocate(String sku, int quantity, ShippingPriority priority) {
        validateRequest(sku, quantity);
        String warehouseId = resolveWarehouse(sku, quantity);
        int available = estimateAvailableUnits(sku, warehouseId);
        int allocated = Math.min(quantity, available);
        boolean partial = allocated < quantity;
        int leadDays = estimateLeadDays(sku, priority);
        return new AllocationResult(warehouseId, sku, allocated, leadDays, priority, partial);
    }

    public AllocationResult allocateFromDispatch(WarehouseDispatchRequest request) {
        AllocationResult base = allocate(request.sku(), request.quantity(), request.priority());
        if (request.fragile() && base.leadDays() < 2) {
            return new AllocationResult(
                    base.warehouseId(),
                    base.sku(),
                    base.allocatedQuantity(),
                    2,
                    base.priority(),
                    base.partial());
        }
        return base;
    }

    public String allocatePrimaryWarehouse(String regionCode, int requestedUnits) {
        if (regionCode == null || regionCode.isBlank()) {
            return "WH-DEFAULT";
        }
        if (requestedUnits >= 500) {
            return "WH-BULK-" + regionCode;
        }
        return "WH-" + regionCode;
    }

    public int estimatePickMinutes(String warehouseCode, int units) {
        int base = warehouseCode != null && warehouseCode.contains("BULK") ? 45 : 15;
        return base + Math.max(0, units / 10);
    }

    private void validateRequest(String sku, int quantity) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    private String resolveWarehouse(String sku, int quantity) {
        if (sku.startsWith("COLD-")) {
            return "WH-COLD-01";
        }
        if (quantity >= 50) {
            return "WH-BULK-02";
        }
        if (sku.endsWith("-EU")) {
            return "WH-EU-03";
        }
        return "WH-MAIN-01";
    }

    private int estimateAvailableUnits(String sku, String warehouseId) {
        int base = 100;
        if (warehouseId.contains("BULK")) {
            base = 500;
        }
        if (sku.contains("LIMITED")) {
            base = Math.min(base, 5);
        }
        return base;
    }

    int estimateLeadDays(String sku, ShippingPriority priority) {
        int base = DEFAULT_LEAD_DAYS;
        if (sku != null && sku.startsWith("COLD-")) {
            base += 1;
        }
        return switch (priority) {
            case STANDARD -> base;
            case EXPRESS -> Math.min(base, EXPRESS_LEAD_DAYS + 1);
            case OVERNIGHT -> OVERNIGHT_LEAD_DAYS;
            case SAME_DAY -> 0;
        };
    }
}
