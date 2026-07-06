package com.sample.api;

import com.sample.billing.BillingContext;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Discount policy port for checkout pricing adjustments.
 */
public interface DiscountPolicy {

    BigDecimal applyDiscount(BigDecimal subtotal, BillingContext context);

    BigDecimal applyTierDiscount(String tierCode, BigDecimal subtotal);

    boolean isPromotionActive(String promotionCode);

    /**
     * Returns true when subtotal is non-null and non-negative.
     */
    default boolean isValidSubtotal(BigDecimal subtotal) {
        return subtotal != null && subtotal.signum() >= 0;
    }

    /**
     * Applies a discount when subtotal and context are valid.
     */
    default BigDecimal applyDiscountIfValid(BigDecimal subtotal, BillingContext context) {
        if (!isValidSubtotal(subtotal) || context == null) {
            return subtotal == null ? BigDecimal.ZERO : subtotal;
        }
        BigDecimal discounted = applyDiscount(subtotal, context);
        return discounted == null ? subtotal : discounted.max(BigDecimal.ZERO);
    }

    /**
     * Applies a tier discount when tier code and subtotal are valid.
     */
    default BigDecimal applyTierDiscountIfValid(String tierCode, BigDecimal subtotal) {
        if (tierCode == null || tierCode.isBlank() || !isValidSubtotal(subtotal)) {
            return subtotal == null ? BigDecimal.ZERO : subtotal;
        }
        BigDecimal discounted = applyTierDiscount(tierCode, subtotal);
        return discounted == null ? subtotal : discounted.max(BigDecimal.ZERO);
    }

    /**
     * Returns true when the promotion code is non-blank and active.
     */
    default boolean isActivePromotion(String promotionCode) {
        return promotionCode != null && !promotionCode.isBlank() && isPromotionActive(promotionCode);
    }

    /**
     * Computes discount savings as the difference between subtotal and discounted amount.
     */
    default BigDecimal discountSavings(BigDecimal subtotal, BillingContext context) {
        if (!isValidSubtotal(subtotal)) {
            return BigDecimal.ZERO;
        }
        BigDecimal discounted = applyDiscountIfValid(subtotal, context);
        return subtotal.subtract(discounted).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Returns the effective subtotal after tier discount, rounded for billing.
     */
    default BigDecimal effectiveSubtotal(String tierCode, BigDecimal subtotal) {
        return applyTierDiscountIfValid(tierCode, subtotal).setScale(2, RoundingMode.HALF_UP);
    }
}
