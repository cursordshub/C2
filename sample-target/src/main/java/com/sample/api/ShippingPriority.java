package com.sample.api;

/**
 * Carrier service levels used by shipping rate and fulfillment logic.
 */
public enum ShippingPriority {
    STANDARD("Standard", 5, false),
    EXPRESS("Express", 2, true),
    OVERNIGHT("Overnight", 1, true),
    SAME_DAY("Same Day", 0, true);

    private final String displayName;
    private final int maxLeadDays;
    private final boolean expedited;

    ShippingPriority(String displayName, int maxLeadDays, boolean expedited) {
        this.displayName = displayName;
        this.maxLeadDays = maxLeadDays;
        this.expedited = expedited;
    }

    public String displayName() {
        return displayName;
    }

    public int maxLeadDays() {
        return maxLeadDays;
    }

    public boolean isExpedited() {
        return expedited;
    }

    /**
     * Returns true when this priority requires express handling.
     */
    public boolean requiresExpressHandling() {
        return expedited;
    }

    /**
     * Resolves a priority from a case-insensitive code, defaulting to STANDARD.
     */
    public static ShippingPriority fromCode(String code) {
        if (code == null || code.isBlank()) {
            return STANDARD;
        }
        try {
            return valueOf(code.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            return STANDARD;
        }
    }

    /**
     * Returns the slowest non-expedited priority.
     */
    public static ShippingPriority defaultPriority() {
        return STANDARD;
    }

    /**
     * Returns true when the given priority is faster than STANDARD.
     */
    public boolean isFasterThan(ShippingPriority other) {
        if (other == null) {
            return expedited;
        }
        return maxLeadDays < other.maxLeadDays;
    }
}
