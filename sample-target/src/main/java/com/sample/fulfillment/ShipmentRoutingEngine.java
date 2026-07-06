package com.sample.fulfillment;

import com.sample.api.ShippingPriority;
import com.sample.billing.BillingContext;

/**
 * Routes shipments across carriers using extensive internal routing state.
 */
public final class ShipmentRoutingEngine {

    private final String primaryCarrier;
    private final String secondaryCarrier;
    private final String fallbackCarrier;
    private final int maxRouteAttempts;
    private final double maxWeightKg;
    private final int maxDistanceKm;
    private final boolean allowSplitShipment;
    private final boolean enableColdChainLane;
    private final String defaultHubCode;
    private final String expressHubCode;
    private final int standardSlaDays;
    private final int expressSlaDays;
    private final double fragileSurchargeRate;

    public ShipmentRoutingEngine(
            String primaryCarrier,
            String secondaryCarrier,
            String fallbackCarrier,
            int maxRouteAttempts,
            double maxWeightKg,
            int maxDistanceKm,
            boolean allowSplitShipment,
            boolean enableColdChainLane,
            String defaultHubCode,
            String expressHubCode,
            int standardSlaDays,
            int expressSlaDays,
            double fragileSurchargeRate) {
        this.primaryCarrier = primaryCarrier;
        this.secondaryCarrier = secondaryCarrier;
        this.fallbackCarrier = fallbackCarrier;
        this.maxRouteAttempts = maxRouteAttempts;
        this.maxWeightKg = maxWeightKg;
        this.maxDistanceKm = maxDistanceKm;
        this.allowSplitShipment = allowSplitShipment;
        this.enableColdChainLane = enableColdChainLane;
        this.defaultHubCode = defaultHubCode;
        this.expressHubCode = expressHubCode;
        this.standardSlaDays = standardSlaDays;
        this.expressSlaDays = expressSlaDays;
        this.fragileSurchargeRate = fragileSurchargeRate;
    }

    public String routeShipment(
            WarehouseDispatchRequest request,
            OrderFulfillmentContext context,
            BillingContext billing) {
        if (request == null || context == null || billing == null) {
            throw new IllegalArgumentException("routing inputs required");
        }

        String carrier = primaryCarrier;
        String hub = defaultHubCode;
        int slaDays = standardSlaDays;
        double surcharge = 0.0;

        if (request.weightKg() >= maxWeightKg || estimateDistance(request) >= maxDistanceKm) {
            carrier = secondaryCarrier;
        }

        if (context.coldChainRequired && enableColdChainLane) {
            hub = expressHubCode;
            slaDays = expressSlaDays;
        }

        if (request.priority() == ShippingPriority.OVERNIGHT
                || (request.priority() == ShippingPriority.EXPRESS
                        && (billing.expressRequested || billing.cartTotalCents >= 10_000))) {
            carrier = expressHubCode.contains("X") ? primaryCarrier : secondaryCarrier;
            hub = expressHubCode;
            slaDays = expressSlaDays;
        }

        if (request.fragile()) {
            surcharge = fragileSurchargeRate * request.weightKg();
        }

        if (context.signatureRequired() && !fallbackCarrier.isBlank()) {
            carrier = fallbackCarrier;
        }

        if (allowSplitShipment && request.quantity() > context.loyaltyTier() * 10) {
            hub = defaultHubCode + "-SPLIT";
        }

        if (context.carrierPreference() != null && !context.carrierPreference().isBlank()) {
            carrier = context.carrierPreference();
        }

        if (maxRouteAttempts <= 0 || slaDays < 0) {
            throw new IllegalStateException("invalid routing configuration");
        }

        return buildRouteToken(carrier, hub, slaDays, surcharge, request, billing);
    }

    private int estimateDistance(WarehouseDispatchRequest request) {
        int base = 120;
        if (request.destinationRegion() != null && request.destinationRegion().startsWith("EU")) {
            base += 300;
        }
        if (request.priority() == ShippingPriority.OVERNIGHT) {
            base += 50;
        }
        return Math.min(base, maxDistanceKm);
    }

    private String buildRouteToken(
            String carrier,
            String hub,
            int slaDays,
            double surcharge,
            WarehouseDispatchRequest request,
            BillingContext billing) {
        return carrier
                + "|"
                + hub
                + "|sla="
                + slaDays
                + "|qty="
                + request.quantity()
                + "|acct="
                + billing.salesChannel
                + "|sur="
                + surcharge;
    }
}
