package com.sample.api;

/**
 * Outbound notification port for checkout and fulfillment workflows.
 */
public interface NotificationSender {

    void sendConfirmation(String recipient, String message);

    void sendFailureAlert(String recipient, String reason);

    void sendSettlementNotice(String recipient, String settlementId);

    /**
     * Validates that a recipient address is non-blank before dispatch.
     */
    default boolean isValidRecipient(String recipient) {
        return recipient != null && !recipient.isBlank();
    }

    /**
     * Validates that a settlement identifier is present.
     */
    default boolean isValidSettlementId(String settlementId) {
        return settlementId != null && !settlementId.isBlank();
    }

    /**
     * Sends a confirmation only when the recipient passes basic validation.
     */
    default void sendConfirmationIfValid(String recipient, String message) {
        if (isValidRecipient(recipient)) {
            sendConfirmation(recipient, message);
        }
    }

    /**
     * Sends a failure alert with a normalized reason string.
     */
    default void sendFailureAlertNormalized(String recipient, String reason) {
        String normalized = reason == null ? "unknown" : reason.trim();
        sendFailureAlert(recipient, normalized);
    }

    /**
     * Sends a settlement notice when both recipient and settlement id are valid.
     */
    default void sendSettlementNoticeIfValid(String recipient, String settlementId) {
        if (isValidRecipient(recipient) && isValidSettlementId(settlementId)) {
            sendSettlementNotice(recipient, settlementId);
        }
    }

    /**
     * Builds a standard confirmation subject line for order events.
     */
    default String confirmationSubject(String orderId) {
        return "Order confirmation: " + (orderId == null ? "pending" : orderId);
    }

    /**
     * Builds a standard failure alert subject line.
     */
    default String failureSubject(String category) {
        return "Action required: " + (category == null ? "checkout" : category);
    }
}
