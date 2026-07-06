package com.sample.catalog;

/**
 * Simple pricing service with static configuration block.
 */
public final class PricingService {

    private static final double TAX_RATE;

    static {
        double configured = 0.08;
        String override = System.getProperty("sample.tax.rate");
        if (override != null && !override.isBlank()) {
            configured = Double.parseDouble(override);
        }
        TAX_RATE = configured;
    }

    private final double baseUnitPrice;
    private final double vipDiscountRate;
    private final double bulkThreshold;
    private final double bulkDiscountRate;
    private final boolean roundToCents;
    private final String priceListVersion;

    public PricingService() {
        this(10.0, 0.10, 5, 0.05, true, "2026-Q2");
    }

    PricingService(
            double baseUnitPrice,
            double vipDiscountRate,
            double bulkThreshold,
            double bulkDiscountRate,
            boolean roundToCents,
            String priceListVersion) {
        this.baseUnitPrice = baseUnitPrice;
        this.vipDiscountRate = vipDiscountRate;
        this.bulkThreshold = bulkThreshold;
        this.bulkDiscountRate = bulkDiscountRate;
        this.roundToCents = roundToCents;
        this.priceListVersion = priceListVersion;
    }

    public double priceFor(Customer customer, int quantity) {
        if (customer == null) {
            throw new IllegalArgumentException("customer required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        double subtotal = baseUnitPrice * quantity;
        if (customer.isVip()) {
            subtotal -= subtotal * vipDiscountRate;
        }
        if (quantity >= bulkThreshold) {
            subtotal -= subtotal * bulkDiscountRate;
        }

        double withTax = subtotal + subtotal * TAX_RATE;
        if (roundToCents) {
            withTax = Math.round(withTax * 100.0) / 100.0;
        }
        return withTax;
    }

    public double taxRate() {
        return TAX_RATE;
    }

    public String priceListVersion() {
        return priceListVersion;
    }

    public double estimateLineTotal(int quantity) {
        return baseUnitPrice * quantity;
    }
}
