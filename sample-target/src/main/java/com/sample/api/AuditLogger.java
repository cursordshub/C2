package com.sample.api;

/**
 * Audit trail port for billing and fulfillment events.
 */
public interface AuditLogger {

    void logEvent(String category, String detail);

    void logError(String category, String detail);

    void logSettlementAudit(String settlementId, String outcome);

    /**
     * Returns true when the category is non-blank.
     */
    default boolean isValidCategory(String category) {
        return category != null && !category.isBlank();
    }

    /**
     * Returns true when the detail payload is non-blank.
     */
    default boolean isValidDetail(String detail) {
        return detail != null && !detail.isBlank();
    }

    /**
     * Logs an event only when both category and detail are present.
     */
    default void logEventIfValid(String category, String detail) {
        if (isValidCategory(category) && isValidDetail(detail)) {
            logEvent(category, detail);
        }
    }

    /**
     * Logs an error with a trimmed detail message.
     */
    default void logErrorTrimmed(String category, String detail) {
        String trimmed = detail == null ? "" : detail.trim();
        logError(category, trimmed);
    }

    /**
     * Logs settlement audit when the settlement id is present.
     */
    default void logSettlementAuditIfValid(String settlementId, String outcome) {
        if (settlementId != null && !settlementId.isBlank()) {
            logSettlementAudit(settlementId, outcome == null ? "unknown" : outcome);
        }
    }

    /**
     * Formats a combined audit line for structured downstream sinks.
     */
    default String formatAuditLine(String category, String detail) {
        return (category == null ? "general" : category) + " | " + (detail == null ? "" : detail);
    }

    /**
     * Formats a settlement audit line with a stable prefix.
     */
    default String formatSettlementLine(String settlementId, String outcome) {
        return "SETTLEMENT:" + settlementId + ":" + (outcome == null ? "pending" : outcome);
    }
}
