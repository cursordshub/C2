package com.sample.billing;

import com.sample.api.PaymentStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Immutable outcome of a checkout attempt including payment and warehouse details.
 */
public final class CheckoutResult {

    private final String orderId;
    private final String transactionId;
    private final BigDecimal totalCharged;
    private final boolean success;
    private final PaymentStatus paymentStatus;
    private final String warehouseCode;

    public CheckoutResult(
            String orderId,
            String transactionId,
            BigDecimal totalCharged,
            boolean success,
            PaymentStatus paymentStatus,
            String warehouseCode) {
        this.orderId = orderId;
        this.transactionId = transactionId;
        this.totalCharged = totalCharged;
        this.success = success;
        this.paymentStatus = paymentStatus;
        this.warehouseCode = warehouseCode;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public BigDecimal getTotalCharged() {
        return totalCharged;
    }

    public boolean isSuccess() {
        return success;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public String getWarehouseCode() {
        return warehouseCode;
    }

    /**
     * Returns true when checkout succeeded and payment status indicates capture.
     */
    public boolean isFullySettled() {
        return success && paymentStatus != null && paymentStatus.isPaid();
    }

    /**
     * Returns the total charged rounded to two decimal places.
     */
    public BigDecimal normalizedTotalCharged() {
        if (totalCharged == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return totalCharged.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Returns true when a warehouse code was assigned.
     */
    public boolean hasWarehouseAssignment() {
        return warehouseCode != null && !warehouseCode.isBlank();
    }

    /**
     * Returns a short summary string for audit logs.
     */
    public String auditSummary() {
        return "order=" + orderId + ",success=" + success + ",status=" + paymentStatus;
    }
}
