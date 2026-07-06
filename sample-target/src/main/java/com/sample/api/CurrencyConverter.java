package com.sample.api;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Currency conversion port for multi-region billing.
 */
public interface CurrencyConverter {

    BigDecimal convertToLocal(BigDecimal amount, String targetCurrency);

    BigDecimal convertFromLocal(BigDecimal amount, String sourceCurrency);

    BigDecimal currentExchangeRate(String fromCurrency, String toCurrency);

    /**
     * Returns true when the currency code is non-blank.
     */
    default boolean isValidCurrency(String currencyCode) {
        return currencyCode != null && !currencyCode.isBlank();
    }

    /**
     * Returns true when the amount is non-null and non-negative.
     */
    default boolean isValidAmount(BigDecimal amount) {
        return amount != null && amount.signum() >= 0;
    }

    /**
     * Converts to local currency when both amount and target currency are valid.
     */
    default BigDecimal convertToLocalIfValid(BigDecimal amount, String targetCurrency) {
        if (!isValidAmount(amount) || !isValidCurrency(targetCurrency)) {
            return amount == null ? BigDecimal.ZERO : amount;
        }
        return convertToLocal(amount, targetCurrency);
    }

    /**
     * Converts from local currency when both amount and source currency are valid.
     */
    default BigDecimal convertFromLocalIfValid(BigDecimal amount, String sourceCurrency) {
        if (!isValidAmount(amount) || !isValidCurrency(sourceCurrency)) {
            return amount == null ? BigDecimal.ZERO : amount;
        }
        return convertFromLocal(amount, sourceCurrency);
    }

    /**
     * Returns the exchange rate or ONE when currencies match or inputs are invalid.
     */
    default BigDecimal safeExchangeRate(String fromCurrency, String toCurrency) {
        if (!isValidCurrency(fromCurrency) || !isValidCurrency(toCurrency)) {
            return BigDecimal.ONE;
        }
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return BigDecimal.ONE;
        }
        return currentExchangeRate(fromCurrency, toCurrency);
    }

    /**
     * Rounds a converted amount to two decimal places for billing display.
     */
    default BigDecimal roundForBilling(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
