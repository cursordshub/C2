package com.sample.catalog;

import com.sample.api.OrderRepository;
import com.sample.billing.CommercialInvoiceDraft;
import com.sample.fulfillment.OrderFulfillmentContext;
import java.util.Objects;

/**
 * Order aggregate coordinating customer, pricing, and payment state.
 */
public final class Order {

    private final String id;
    private final Customer customer;
    private final PricingService pricingService;
    private PaymentStatus status;
    private int lineCount;
    private double cachedSubtotal;
    private String notes;

    public Order(String id, Customer customer, PricingService pricingService) {
        this.id = Objects.requireNonNull(id, "id");
        this.customer = Objects.requireNonNull(customer, "customer");
        this.pricingService = Objects.requireNonNull(pricingService, "pricingService");
        this.status = PaymentStatus.PENDING;
        this.lineCount = 1;
        this.cachedSubtotal = 0.0;
        this.notes = "";
    }

    public double total(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        double base = pricingService.priceFor(customer, quantity);
        if (customer.qualifiesForBulkDiscount() && quantity >= 10) {
            base *= 0.95;
        }
        cachedSubtotal = base;
        lineCount = quantity;
        return base;
    }

    public void markPaid() {
        if (status == PaymentStatus.CANCELLED) {
            throw new IllegalStateException("cancelled order cannot be paid");
        }
        status = PaymentStatus.PAID;
    }

    public void authorize() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("only pending orders can be authorized");
        }
        status = PaymentStatus.AUTHORIZED;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public PricingService getPricingService() {
        return pricingService;
    }

    public int getLineCount() {
        return lineCount;
    }

    public double getCachedSubtotal() {
        return cachedSubtotal;
    }

    public void setNotes(String notes) {
        this.notes = notes == null ? "" : notes;
    }

    public String getNotes() {
        return notes;
    }

    public void persistDraft(OrderRepository repository, CommercialInvoiceDraft draft) {
        repository.saveInvoiceDraft(draft);
        repository.attachFulfillmentContext(id, null);
    }

    public boolean isReadyForFulfillment() {
        return status == PaymentStatus.PAID || status == PaymentStatus.AUTHORIZED;
    }
}
