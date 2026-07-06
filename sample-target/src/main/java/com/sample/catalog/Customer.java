package com.sample.catalog;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Legacy customer aggregate used by catalog checkout and pricing.
 */
public final class Customer {

    private final String id;
    private final String name;
    private final String email;
    private final String regionCode;
    private final int loyaltyPoints;
    private final boolean vip;
    private final LocalDate memberSince;
    private final String preferredCurrency;
    private final int failedPaymentCount;
    private final String referralCode;

    public Customer(String id, String name) {
        this(id, name, id + "@example.com", "US", 0, false, LocalDate.now(), "USD", 0, "");
    }

    public Customer(
            String id,
            String name,
            String email,
            String regionCode,
            int loyaltyPoints,
            boolean vip,
            LocalDate memberSince,
            String preferredCurrency,
            int failedPaymentCount,
            String referralCode) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.email = email;
        this.regionCode = regionCode;
        this.loyaltyPoints = loyaltyPoints;
        this.vip = vip;
        this.memberSince = memberSince;
        this.preferredCurrency = preferredCurrency;
        this.failedPaymentCount = failedPaymentCount;
        this.referralCode = referralCode;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public int getLoyaltyPoints() {
        return loyaltyPoints;
    }

    public boolean isVip() {
        return vip;
    }

    public LocalDate getMemberSince() {
        return memberSince;
    }

    public String getPreferredCurrency() {
        return preferredCurrency;
    }

    public int getFailedPaymentCount() {
        return failedPaymentCount;
    }

    public String getReferralCode() {
        return referralCode;
    }

    public boolean qualifiesForBulkDiscount() {
        return vip || loyaltyPoints >= 500;
    }

    public boolean hasCleanPaymentHistory() {
        return failedPaymentCount == 0;
    }

    public String displayLabel() {
        return name + " (" + id + ")";
    }
}
