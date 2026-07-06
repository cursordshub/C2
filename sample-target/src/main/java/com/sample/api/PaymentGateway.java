package com.sample.api;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Payment gateway port for authorization, capture, and refund operations.
 */
public interface PaymentGateway {

    String charge(String accountToken, BigDecimal amount);

    boolean verifyFunds(String accountToken, BigDecimal amount);

    PaymentStatus capture(String authorizationId, BigDecimal amount);

    void refund(String transactionId, BigDecimal amount);

    /**
     * Returns true when the account token is non-blank.
     */
    default boolean isValidAccountToken(String accountToken) {
        return accountToken != null && !accountToken.isBlank();
    }

    /**
     * Returns true when the amount is non-null and positive.
     */
    default boolean isChargeableAmount(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }

    /**
     * Verifies funds only when token and amount are valid.
     */
    default boolean verifyFundsIfValid(String accountToken, BigDecimal amount) {
        if (!isValidAccountToken(accountToken) || !isChargeableAmount(amount)) {
            return false;
        }
        return verifyFunds(accountToken, amount);
    }

    /**
     * Charges the account when token and amount pass validation.
     */
    default String chargeIfValid(String accountToken, BigDecimal amount) {
        if (!isValidAccountToken(accountToken) || !isChargeableAmount(amount)) {
            return null;
        }
        return charge(accountToken, amount);
    }

    /**
     * Captures an authorization when id and amount are valid.
     */
    default PaymentStatus captureIfValid(String authorizationId, BigDecimal amount) {
        if (authorizationId == null || authorizationId.isBlank() || !isChargeableAmount(amount)) {
            return PaymentStatus.FAILED;
        }
        return capture(authorizationId, amount);
    }

    /**
     * Refunds a transaction when id and amount are valid.
     */
    default void refundIfValid(String transactionId, BigDecimal amount) {
        if (transactionId != null && !transactionId.isBlank() && isChargeableAmount(amount)) {
            refund(transactionId, amount);
        }
    }

    /**
     * Rounds a charge amount to two decimal places.
     */
    default BigDecimal normalizeChargeAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
