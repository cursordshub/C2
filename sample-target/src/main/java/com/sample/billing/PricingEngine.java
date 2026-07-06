package com.sample.billing;

import com.sample.api.CurrencyConverter;
import com.sample.api.DiscountPolicy;
import com.sample.api.ProductCatalog;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class PricingEngine {

    private final ProductCatalog productCatalog;
    private final CurrencyConverter currencyConverter;
    private final DiscountPolicy discountPolicy;
    private final AbstractPricingRule defaultRule;
    private final AbstractPricingRule premiumRule;

    public PricingEngine(
            ProductCatalog productCatalog,
            CurrencyConverter currencyConverter,
            DiscountPolicy discountPolicy) {
        this.productCatalog = productCatalog;
        this.currencyConverter = currencyConverter;
        this.discountPolicy = discountPolicy;
        this.defaultRule = new StandardPricingRule();
        this.premiumRule = new PremiumPricingRule();
    }

    public BigDecimal quoteLineTotal(String sku, int quantity, BillingContext context) {
        if (!productCatalog.isActiveSku(sku)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal unitPrice = quantity >= 10
                ? productCatalog.getBulkPrice(sku, quantity)
                : productCatalog.getBasePrice(sku);
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal discounted = discountPolicy.applyDiscount(subtotal, context);
        BigDecimal localized = currencyConverter.convertToLocal(discounted, context.currencyCode);
        return applyRule(localized, context, defaultRule);
    }

    public BigDecimal quoteWithFallback(
            String sku,
            int quantity,
            BillingContext context,
            String fallbackSku) {
        BigDecimal primary = quoteLineTotal(sku, quantity, context);
        if (primary.compareTo(BigDecimal.ZERO) > 0) {
            return primary;
        }
        if (fallbackSku == null || fallbackSku.isBlank()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal fallbackBase = productCatalog.getBasePrice(fallbackSku);
        BigDecimal fallbackSubtotal = fallbackBase.multiply(BigDecimal.valueOf(Math.max(1, quantity)));
        BigDecimal discounted = discountPolicy.applyDiscount(fallbackSubtotal, context);
        BigDecimal localized = currencyConverter.convertFromLocal(discounted, context.currencyCode);
        return applyRule(localized, context, premiumRule);
    }

    public BigDecimal quotePremiumBundle(String sku, int quantity, BillingContext context) {
        BigDecimal base = productCatalog.getBasePrice(sku);
        BigDecimal bulk = productCatalog.getBulkPrice(sku, quantity);
        BigDecimal blended = base.add(bulk).divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
        BigDecimal withDiscount = discountPolicy.applyDiscount(blended, context);
        BigDecimal converted = currencyConverter.convertToLocal(withDiscount, context.currencyCode);
        return applyRule(converted, context, premiumRule);
    }

    public BigDecimal estimateTaxableSubtotal(String sku, int quantity, BillingContext context) {
        BigDecimal line = quoteLineTotal(sku, quantity, context);
        if (line.compareTo(BigDecimal.valueOf(100)) >= 0 && context.expressRequested) {
            return line.multiply(BigDecimal.valueOf(0.98)).setScale(2, RoundingMode.HALF_UP);
        }
        return line;
    }

    public BigDecimal quoteCategoryAdjustedTotal(String sku, int quantity, BillingContext context) {
        String category = productCatalog.resolveCategory(sku);
        BigDecimal base = quoteLineTotal(sku, quantity, context);
        if (category != null && category.startsWith("PREMIUM")) {
            return applyRule(base, context, premiumRule);
        }
        if (category != null && category.startsWith("CLEARANCE")) {
            return applyRule(base.multiply(BigDecimal.valueOf(0.85)), context, defaultRule);
        }
        return base;
    }

    public boolean isSkuQuotable(String sku, BillingContext context) {
        if (!productCatalog.isActiveSku(sku)) {
            return false;
        }
        BigDecimal probe = productCatalog.getBasePrice(sku);
        return probe != null
                && probe.compareTo(BigDecimal.ZERO) > 0
                && context.itemQuantity > 0;
    }

    private BigDecimal applyRule(BigDecimal amount, BillingContext context, AbstractPricingRule rule) {
        BigDecimal adjusted = rule.adjust(amount, context);
        if (adjusted.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return adjusted.setScale(2, RoundingMode.HALF_UP);
    }

    public abstract static class AbstractPricingRule {
        abstract BigDecimal adjust(BigDecimal amount, BillingContext context);
    }

    public static final class StandardPricingRule extends AbstractPricingRule {
        @Override
        BigDecimal adjust(BigDecimal amount, BillingContext context) {
            if (context.itemQuantity >= 5) {
                return amount.multiply(BigDecimal.valueOf(0.97));
            }
            return amount;
        }
    }

    public static final class PremiumPricingRule extends AbstractPricingRule {
        @Override
        BigDecimal adjust(BigDecimal amount, BillingContext context) {
            if ("WEB".equals(context.salesChannel) && context.cartTotalCents >= 10_000) {
                return amount.multiply(BigDecimal.valueOf(0.95));
            }
            if (context.expressRequested) {
                return amount.multiply(BigDecimal.valueOf(1.05));
            }
            return amount;
        }
    }
}
