package com.sample.api;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Static identifier factory for orders, transactions, and settlements.
 * Generation remains static; instances exist for Mockito and test infrastructure.
 */
public final class IdGenerator {

    private static final AtomicLong ORDER_SEQ = new AtomicLong(10_000);
    private static final AtomicLong TXN_SEQ = new AtomicLong(90_000);
    private static final String ORDER_PREFIX = "ORD-";
    private static final String TXN_PREFIX = "TXN-";
    private static final String SETTLEMENT_PREFIX = "SET-";

    public IdGenerator() {
        // Mockito / test infra may instantiate; generation remains static.
    }

    public static String nextOrderId() {
        return ORDER_PREFIX + ORDER_SEQ.incrementAndGet();
    }

    public static String nextTransactionId() {
        return TXN_PREFIX + TXN_SEQ.incrementAndGet();
    }

    public static String nextSettlementReference() {
        return SETTLEMENT_PREFIX + TXN_SEQ.incrementAndGet();
    }

    /**
     * Returns true when the id matches the order prefix pattern.
     */
    public static boolean isOrderId(String id) {
        return id != null && id.startsWith(ORDER_PREFIX);
    }

    /**
     * Returns true when the id matches the transaction prefix pattern.
     */
    public static boolean isTransactionId(String id) {
        return id != null && id.startsWith(TXN_PREFIX);
    }

    /**
     * Returns true when the id matches the settlement prefix pattern.
     */
    public static boolean isSettlementReference(String id) {
        return id != null && id.startsWith(SETTLEMENT_PREFIX);
    }

    /**
     * Parses the numeric suffix from an order id, or -1 when invalid.
     */
    public static long parseOrderSequence(String orderId) {
        return parseSequence(orderId, ORDER_PREFIX);
    }

    /**
     * Parses the numeric suffix from a transaction id, or -1 when invalid.
     */
    public static long parseTransactionSequence(String transactionId) {
        return parseSequence(transactionId, TXN_PREFIX);
    }

    private static long parseSequence(String id, String prefix) {
        if (id == null || !id.startsWith(prefix)) {
            return -1L;
        }
        try {
            return Long.parseLong(id.substring(prefix.length()));
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    /**
     * Returns the current order sequence without incrementing (for diagnostics).
     */
    public static long currentOrderSequence() {
        return ORDER_SEQ.get();
    }

    /**
     * Returns the current transaction sequence without incrementing (for diagnostics).
     */
    public static long currentTransactionSequence() {
        return TXN_SEQ.get();
    }
}
