package com.sample.api;

/**
 * Payment lifecycle states returned by the payment gateway.
 */
public enum PaymentStatus {
    PENDING(false, false),
    AUTHORIZED(false, false),
    CAPTURED(true, true),
    SETTLED(true, true),
    FAILED(true, false),
    REFUNDED(true, false),
    VOIDED(true, false);

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
     * Returns true when funds were captured or settled successfully.
     */
    public boolean isPaid() {
        return this == CAPTURED || this == SETTLED;
    }

    /**
     * Returns true when the payment attempt failed or was voided.
     */
    public boolean isFailure() {
        return this == FAILED || this == VOIDED;
    }

    /**
     * Returns true when the payment is still in progress.
     */
    public boolean isInProgress() {
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
     * Returns a human-readable label for reporting.
     */
    public String displayLabel() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
