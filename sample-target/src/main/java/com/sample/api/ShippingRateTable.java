package com.sample.api;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shipping rate lookup port for weight, distance, and priority pricing.
 */
public interface ShippingRateTable {

    BigDecimal lookupRate(double weightKg, int distanceKm);

    BigDecimal expressMultiplier(ShippingPriority priority);

    BigDecimal regionalFlatFee(String regionCode);

    BigDecimal bulkDiscountRate(int totalUnits);

    /**
     * Returns true when weight is positive.
     */
    default boolean isValidWeight(double weightKg) {
        return weightKg > 0.0;
    }

    /**
     * Returns true when distance is non-negative.
     */
    default boolean isValidDistance(int distanceKm) {
        return distanceKm >= 0;
    }

    /**
     * Looks up a base rate only when weight and distance are valid.
     */
    default BigDecimal lookupRateIfValid(double weightKg, int distanceKm) {
        if (!isValidWeight(weightKg) || !isValidDistance(distanceKm)) {
            return BigDecimal.ZERO;
        }
        return lookupRate(weightKg, distanceKm);
    }

    /**
     * Applies an express multiplier to a base rate, defaulting priority to STANDARD.
     */
    default BigDecimal applyPriorityMultiplier(BigDecimal baseRate, ShippingPriority priority) {
        if (baseRate == null) {
            return BigDecimal.ZERO;
        }
        ShippingPriority effective = priority == null ? ShippingPriority.STANDARD : priority;
        BigDecimal multiplier = expressMultiplier(effective);
        if (multiplier == null) {
            return baseRate;
        }
        return baseRate.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Returns a regional flat fee or zero when the region code is blank.
     */
    default BigDecimal regionalFlatFeeOrZero(String regionCode) {
        if (regionCode == null || regionCode.isBlank()) {
            return BigDecimal.ZERO;
        }
        BigDecimal fee = regionalFlatFee(regionCode);
        return fee == null ? BigDecimal.ZERO : fee;
    }

    /**
     * Returns a bulk discount factor between zero and one inclusive.
     */
    default BigDecimal safeBulkDiscountRate(int totalUnits) {
        if (totalUnits <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = bulkDiscountRate(totalUnits);
        if (rate == null || rate.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return rate.min(BigDecimal.ONE);
    }
}
