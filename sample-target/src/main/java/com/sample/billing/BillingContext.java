package com.sample.billing;

/**
 * Immutable billing context passed through checkout pricing and tax logic.
 */
public final class BillingContext {

    public final String regionCode;
    public final String currencyCode;
    public final int itemQuantity;
    public final int cartTotalCents;
    public final boolean expressRequested;
    public final String salesChannel;

    public BillingContext(
            String regionCode,
            String currencyCode,
            int itemQuantity,
            int cartTotalCents,
            boolean expressRequested,
            String salesChannel) {
        this.regionCode = regionCode;
        this.currencyCode = currencyCode;
        this.itemQuantity = itemQuantity;
        this.cartTotalCents = cartTotalCents;
        this.expressRequested = expressRequested;
        this.salesChannel = salesChannel;
    }

    public String currencyCode() {
        return currencyCode;
    }

    public boolean allowsExpressBilling() {
        return expressRequested || cartTotalCents >= 10_000;
    }

    /**
     * Returns true when the region code is non-blank.
     */
    public boolean hasRegion() {
        return regionCode != null && !regionCode.isBlank();
    }

    /**
     * Returns true when the sales channel is non-blank.
     */
    public boolean hasSalesChannel() {
        return salesChannel != null && !salesChannel.isBlank();
    }

    /**
     * Returns cart total in major currency units (e.g. dollars from cents).
     */
    public double cartTotalMajorUnits() {
        return cartTotalCents / 100.0;
    }

    /**
     * Returns true when the cart contains at least one item.
     */
    public boolean hasItems() {
        return itemQuantity > 0;
    }

    /**
     * Returns true when the cart qualifies for bulk pricing thresholds.
     */
    public boolean qualifiesForBulkPricing() {
        return itemQuantity >= 10 || cartTotalCents >= 25_000;
    }

    /**
     * Returns a normalized region code in upper case, or empty when absent.
     */
    public String normalizedRegionCode() {
        return hasRegion() ? regionCode.trim().toUpperCase() : "";
    }

    /**
     * Returns a short diagnostic label for logging.
     */
    public String diagnosticLabel() {
        return normalizedRegionCode() + "/" + currencyCode + "/qty=" + itemQuantity;
    }

    /**
     * Returns true when express billing is allowed and explicitly requested.
     */
    public boolean requiresExpressHandling() {
        return expressRequested && allowsExpressBilling();
    }
}
