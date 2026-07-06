package com.sample.api;

import com.sample.billing.BillingContext;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tax calculation port for regional and import duty assessments.
 */
public interface TaxCalculator {

    BigDecimal computeTax(BigDecimal taxableAmount, BillingContext context);

    BigDecimal computeRegionalSurcharge(String regionCode, BigDecimal baseAmount);

    BigDecimal computeImportDuty(String sku, BigDecimal declaredValue);

    boolean isRegionTaxExempt(String regionCode);

    /**
     * Returns true when the taxable amount is non-null and non-negative.
     */
    default boolean isValidTaxableAmount(BigDecimal taxableAmount) {
        return taxableAmount != null && taxableAmount.signum() >= 0;
    }

    /**
     * Computes tax when amount and context are valid and region is not exempt.
     */
    default BigDecimal computeTaxIfValid(BigDecimal taxableAmount, BillingContext context) {
        if (!isValidTaxableAmount(taxableAmount) || context == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (isRegionTaxExempt(context.regionCode)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal tax = computeTax(taxableAmount, context);
        return tax == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : tax.max(BigDecimal.ZERO);
    }

    /**
     * Computes regional surcharge when region and base amount are valid.
     */
    default BigDecimal computeRegionalSurchargeIfValid(String regionCode, BigDecimal baseAmount) {
        if (regionCode == null || regionCode.isBlank() || !isValidTaxableAmount(baseAmount)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal surcharge = computeRegionalSurcharge(regionCode, baseAmount);
        return surcharge == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : surcharge.max(BigDecimal.ZERO);
    }

    /**
     * Computes import duty when sku and declared value are valid.
     */
    default BigDecimal computeImportDutyIfValid(String sku, BigDecimal declaredValue) {
        if (sku == null || sku.isBlank() || !isValidTaxableAmount(declaredValue)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal duty = computeImportDuty(sku, declaredValue);
        return duty == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : duty.max(BigDecimal.ZERO);
    }

    /**
     * Returns total tax including regional surcharge for a billing context.
     */
    default BigDecimal totalTaxWithSurcharge(BigDecimal taxableAmount, BillingContext context) {
        BigDecimal baseTax = computeTaxIfValid(taxableAmount, context);
        if (context == null) {
            return baseTax;
        }
        BigDecimal surcharge = computeRegionalSurchargeIfValid(context.regionCode, taxableAmount);
        return baseTax.add(surcharge).setScale(2, RoundingMode.HALF_UP);
    }
}
