package com.sample.fulfillment;

import com.sample.api.ShippingPriority;

public final class OrderFulfillmentContext {

    public final String regionCode;
    public final String currencyCode;
    public final int loyaltyTier;
    public final int maxQuantity;
    public final boolean taxExempt;
    public final String channel;
    public final String warehouseCode;
    public final boolean coldChainRequired;
    public final int allocationPriority;
    public final String carrierPreference;
    public final boolean premiumChannel;
    public final String fulfillmentNotes;

    public OrderFulfillmentContext(
            String regionCode,
            String currencyCode,
            int loyaltyTier,
            int maxQuantity,
            boolean taxExempt,
            String channel,
            String warehouseCode,
            boolean coldChainRequired,
            int allocationPriority,
            String carrierPreference,
            boolean premiumChannel,
            String fulfillmentNotes) {
        this.regionCode = regionCode;
        this.currencyCode = currencyCode;
        this.loyaltyTier = loyaltyTier;
        this.maxQuantity = maxQuantity;
        this.taxExempt = taxExempt;
        this.channel = channel;
        this.warehouseCode = warehouseCode;
        this.coldChainRequired = coldChainRequired;
        this.allocationPriority = allocationPriority;
        this.carrierPreference = carrierPreference;
        this.premiumChannel = premiumChannel;
        this.fulfillmentNotes = fulfillmentNotes;
    }

    public String orderId() {
        return channel + "-" + warehouseCode;
    }

    public String destinationRegion() {
        return regionCode;
    }

    public int lineCount() {
        return allocationPriority;
    }

    public double totalWeightKg() {
        return Math.max(1.0, allocationPriority * 0.5);
    }

    public ShippingPriority priority() {
        return premiumChannel ? ShippingPriority.EXPRESS : ShippingPriority.STANDARD;
    }

    public boolean fragileItems() {
        return coldChainRequired;
    }

    public boolean isPremiumChannel() {
        return premiumChannel;
    }

    public String regionCode() {
        return regionCode;
    }

    public boolean taxExempt() {
        return taxExempt;
    }

    public String channel() {
        return channel;
    }

    public boolean allowsQuantity(int quantity) {
        return quantity > 0 && quantity <= maxQuantity;
    }

    public int loyaltyTier() {
        return loyaltyTier;
    }

    public String fulfillmentNotes() {
        return fulfillmentNotes;
    }

    public boolean signatureRequired() {
        return premiumChannel || loyaltyTier >= 2;
    }

    public String carrierPreference() {
        return carrierPreference;
    }
}
