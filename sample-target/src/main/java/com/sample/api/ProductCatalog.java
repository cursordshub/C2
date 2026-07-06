package com.sample.api;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Product catalog lookup port for pricing and category resolution.
 */
public interface ProductCatalog {

    BigDecimal getBasePrice(String sku);

    BigDecimal getBulkPrice(String sku, int quantity);

    boolean isActiveSku(String sku);

    String resolveCategory(String sku);

    /**
     * Returns true when the sku is non-blank.
     */
    default boolean isValidSku(String sku) {
        return sku != null && !sku.isBlank();
    }

    /**
     * Returns true when quantity is positive.
     */
    default boolean isPositiveQuantity(int quantity) {
        return quantity > 0;
    }

    /**
     * Returns the base price or zero when the sku is invalid.
     */
    default BigDecimal safeBasePrice(String sku) {
        if (!isValidSku(sku)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal price = getBasePrice(sku);
        return price == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : price;
    }

    /**
     * Returns bulk price when sku and quantity are valid, otherwise base price.
     */
    default BigDecimal priceForQuantity(String sku, int quantity) {
        if (!isValidSku(sku) || !isPositiveQuantity(quantity)) {
            return safeBasePrice(sku);
        }
        BigDecimal bulk = getBulkPrice(sku, quantity);
        return bulk == null ? safeBasePrice(sku) : bulk;
    }

    /**
     * Returns true when the sku is active and valid.
     */
    default boolean isPurchasable(String sku) {
        return isValidSku(sku) && isActiveSku(sku);
    }

    /**
     * Resolves a category or returns a default when sku is invalid.
     */
    default String resolveCategoryOrDefault(String sku, String defaultCategory) {
        if (!isValidSku(sku)) {
            return defaultCategory == null ? "GENERAL" : defaultCategory;
        }
        String category = resolveCategory(sku);
        return category == null || category.isBlank() ? defaultCategory : category;
    }

    /**
     * Computes extended line total for a sku and quantity.
     */
    default BigDecimal lineTotal(String sku, int quantity) {
        return priceForQuantity(sku, quantity).multiply(BigDecimal.valueOf(Math.max(0, quantity)))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
