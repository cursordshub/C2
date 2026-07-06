package com.sample.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Draft commercial invoice persisted alongside checkout for B2B settlements.
 */
public final class CommercialInvoiceDraft {

    public final String invoiceNumber;
    public final String buyerName;
    public final String sellerName;
    public final int lineItemCount;
    public final BigDecimal subtotal;
    public final BigDecimal taxAmount;
    public final BigDecimal shippingAmount;
    public final BigDecimal discountAmount;
    public final String currencyCode;
    public final LocalDate issueDate;
    public final LocalDate dueDate;
    public final String paymentTerms;

    public CommercialInvoiceDraft(
            String invoiceNumber,
            String buyerName,
            String sellerName,
            int lineItemCount,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal shippingAmount,
            BigDecimal discountAmount,
            String currencyCode,
            LocalDate issueDate,
            LocalDate dueDate,
            String paymentTerms) {
        this.invoiceNumber = invoiceNumber;
        this.buyerName = buyerName;
        this.sellerName = sellerName;
        this.lineItemCount = lineItemCount;
        this.subtotal = subtotal;
        this.taxAmount = taxAmount;
        this.shippingAmount = shippingAmount;
        this.discountAmount = discountAmount;
        this.currencyCode = currencyCode;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.paymentTerms = paymentTerms;
    }

    public String invoiceNumber() {
        return invoiceNumber;
    }

    String draftId() {
        return invoiceNumber;
    }

    /**
     * Returns the invoice grand total including tax and shipping minus discounts.
     */
    public BigDecimal grandTotal() {
        BigDecimal base = safe(subtotal).add(safe(taxAmount)).add(safe(shippingAmount));
        return base.subtract(safe(discountAmount)).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Returns true when the draft has at least one line item.
     */
    public boolean hasLineItems() {
        return lineItemCount > 0;
    }

    /**
     * Returns true when the invoice number is non-blank.
     */
    public boolean hasInvoiceNumber() {
        return invoiceNumber != null && !invoiceNumber.isBlank();
    }

    /**
     * Returns days until due date from today, or zero when overdue or dates missing.
     */
    public long daysUntilDue() {
        if (dueDate == null) {
            return 0L;
        }
        return Math.max(0L, LocalDate.now().until(dueDate).getDays());
    }

    /**
     * Returns a short summary for repository indexing.
     */
    public String indexKey() {
        return (invoiceNumber == null ? "draft" : invoiceNumber) + ":" + currencyCode;
    }

    private static BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
