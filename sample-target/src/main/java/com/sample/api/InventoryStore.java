package com.sample.api;

/**
 * Inventory reservation port for checkout and allocation flows.
 */
public interface InventoryStore {

    boolean reserve(String sku, int quantity);

    int availableQuantity(String sku);

    void release(String sku, int quantity);

    boolean isBackorderAllowed(String sku);

    /**
     * Returns true when the sku is non-blank.
     */
    default boolean isValidSku(String sku) {
        return sku != null && !sku.isBlank();
    }

    /**
     * Returns true when quantity is a positive reservation amount.
     */
    default boolean isPositiveQuantity(int quantity) {
        return quantity > 0;
    }

    /**
     * Returns true when the requested quantity can be satisfied from stock.
     */
    default boolean canFulfill(String sku, int quantity) {
        return isValidSku(sku) && isPositiveQuantity(quantity) && availableQuantity(sku) >= quantity;
    }

    /**
     * Reserves stock only when the sku and quantity are valid and stock is available.
     */
    default boolean reserveIfAvailable(String sku, int quantity) {
        if (!canFulfill(sku, quantity)) {
            return false;
        }
        return reserve(sku, quantity);
    }

    /**
     * Releases stock only when sku and quantity are valid.
     */
    default void releaseIfValid(String sku, int quantity) {
        if (isValidSku(sku) && isPositiveQuantity(quantity)) {
            release(sku, quantity);
        }
    }

    /**
     * Returns true when backorder is permitted or stock covers the request.
     */
    default boolean canAcceptOrder(String sku, int quantity) {
        return canFulfill(sku, quantity) || (isValidSku(sku) && isBackorderAllowed(sku));
    }

    /**
     * Returns remaining stock after a hypothetical reservation.
     */
    default int remainingAfterReserve(String sku, int quantity) {
        if (!isValidSku(sku) || !isPositiveQuantity(quantity)) {
            return availableQuantity(sku);
        }
        return Math.max(0, availableQuantity(sku) - quantity);
    }
}
