package com.sample.catalog;

/**
 * Catalog-side payment states distinct from gateway lifecycle statuses.
 */
public enum PaymentStatus {
    PENDING(false, false),
    AUTHORIZED(false, false),
    PAID(true, true),
    CANCELLED(true, false);

    private final boolean terminal;
    private final boolean successful;

    PaymentStatus(boolean terminal, boolean successful) {
        this.terminal = terminal;
        this.successful = successful;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean isSuccessful() {
        return successful;
    }

    /**
     * Returns true when the order has been paid in the catalog view.
     */
    public boolean isPaid() {
        return this == PAID;
    }

    /**
     * Returns true when the payment was cancelled.
     */
    public boolean isCancelled() {
        return this == CANCELLED;
    }

    /**
     * Returns true when payment is still awaiting completion.
     */
    public boolean isAwaitingPayment() {
        return this == PENDING || this == AUTHORIZED;
    }

    /**
     * Resolves a status from a case-insensitive name, defaulting to PENDING.
     */
    public static PaymentStatus fromName(String name) {
        if (name == null || name.isBlank()) {
            return PENDING;
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return PENDING;
        }
    }

    /**
     * Returns a display label for customer-facing receipts.
     */
    public String displayLabel() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }

    /**
     * Maps this catalog status to a coarse checkout outcome string.
     */
    public String checkoutOutcome() {
        if (isPaid()) {
            return "success";
        }
        if (isCancelled()) {
            return "cancelled";
        }
        return "pending";
    }
}
