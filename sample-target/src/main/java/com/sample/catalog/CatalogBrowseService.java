package com.sample.catalog;

import com.sample.api.ProductCatalog;
import com.sample.api.TaxCalculator;
import com.sample.billing.BillingContext;
import com.sample.billing.CommercialInvoiceDraft;
import com.sample.fulfillment.FulfillmentCoordinator;
import com.sample.fulfillment.OrderFulfillmentContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Catalog browsing and checkout orchestration across api and billing collaborators.
 */
public final class CatalogBrowseService {

    private final ProductCatalog productCatalog;
    private final TaxCalculator taxCalculator;
    private final PricingService pricingService;

    public CatalogBrowseService(
            ProductCatalog productCatalog, TaxCalculator taxCalculator, PricingService pricingService) {
        this.productCatalog = productCatalog;
        this.taxCalculator = taxCalculator;
        this.pricingService = pricingService;
    }

    public List<String> listActiveSkus(String prefix) {
        List<String> results = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String sku = prefix + "-SKU-" + i;
            if (productCatalog.isActiveSku(sku)) {
                results.add(sku);
            }
        }
        return results;
    }

    public BigDecimal quoteBrowsePrice(String sku, int quantity, Customer customer) {
        if (!productCatalog.isActiveSku(sku)) {
            throw new IllegalArgumentException("inactive sku");
        }
        BigDecimal catalogPrice = productCatalog.getBulkPrice(sku, quantity);
        if (catalogPrice == null || catalogPrice.compareTo(BigDecimal.ZERO) <= 0) {
            catalogPrice = productCatalog.getBasePrice(sku).multiply(BigDecimal.valueOf(quantity));
        }
        if (customer.isVip()) {
            catalogPrice = catalogPrice.multiply(BigDecimal.valueOf(0.92));
        }
        return catalogPrice;
    }

    public CommercialInvoiceDraft prepareInvoiceDraft(
            Customer customer, CustomerAccountProfile profile, String sku, int quantity) {
        if (profile.computeRiskScore() >= 80) {
            throw new IllegalStateException("risk score too high for checkout");
        }

        BigDecimal subtotal = quoteBrowsePrice(sku, quantity, customer);
        BillingContext billing = new BillingContext(
                customer.getRegionCode(),
                customer.getPreferredCurrency(),
                quantity,
                subtotal.multiply(BigDecimal.valueOf(100)).intValue(),
                customer.isVip(),
                customer.isVip() ? "PREMIUM" : "WEB");
        BigDecimal tax = taxCalculator.computeTax(subtotal, billing);
        BigDecimal shipping = BigDecimal.valueOf(pricingService.estimateLineTotal(quantity) * 0.05);

        return new CommercialInvoiceDraft(
                "CAT-" + customer.getId(),
                customer.getName(),
                "Sample Catalog Inc",
                quantity,
                subtotal,
                tax,
                shipping,
                BigDecimal.ZERO,
                billing.currencyCode,
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                "browse:" + profile.segment());
    }

    public Order checkout(
            Customer customer,
            CustomerAccountProfile profile,
            String sku,
            int quantity,
            FulfillmentCoordinator fulfillmentCoordinator) {
        CommercialInvoiceDraft draft = prepareInvoiceDraft(customer, profile, sku, quantity);
        Order order = new Order(draft.invoiceNumber, customer, pricingService);
        order.total(quantity);
        order.authorize();

        OrderFulfillmentContext ctx = new OrderFulfillmentContext(
                customer.getRegionCode(),
                customer.getPreferredCurrency(),
                customer.getLoyaltyPoints() / 100,
                50,
                false,
                customer.isVip() ? "PREMIUM" : "WEB",
                "WH-MAIN-01",
                sku.startsWith("COLD-"),
                customer.isVip() ? 2 : 0,
                "UPS",
                customer.isVip(),
                "checkout:" + draft.invoiceNumber);

        if (fulfillmentCoordinator.canSchedule(sku, quantity)) {
            fulfillmentCoordinator.scheduleShipment(sku, quantity, com.sample.api.ShippingPriority.STANDARD);
        }

        order.setNotes(ctx.fulfillmentNotes());
        return order;
    }

    public boolean isEligibleForExpress(Customer customer, CustomerAccountProfile profile) {
        return customer.hasCleanPaymentHistory()
                && profile.computeRiskScore() < 40
                && customer.getLoyaltyPoints() >= 100;
    }
}
