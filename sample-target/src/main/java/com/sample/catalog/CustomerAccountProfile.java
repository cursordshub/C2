package com.sample.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class CustomerAccountProfile {

    public final String customerId;
    public final String tierCode;
    public final String homeRegion;
    public final BigDecimal lifetimeSpend;
    public final boolean taxExempt;
    public final int loyaltyPoints;
    public final int chargebackCount;
    public final int daysSinceLastLogin;
    public final boolean emailVerified;
    public final boolean phoneVerified;
    public final int supportTickets;
    public final LocalDate accountOpened;

    public CustomerAccountProfile(
            String customerId,
            String tierCode,
            String homeRegion,
            BigDecimal lifetimeSpend,
            boolean taxExempt,
            int loyaltyPoints,
            int chargebackCount,
            int daysSinceLastLogin,
            boolean emailVerified,
            boolean phoneVerified,
            int supportTickets,
            LocalDate accountOpened) {
        this.customerId = customerId;
        this.tierCode = tierCode;
        this.homeRegion = homeRegion;
        this.lifetimeSpend = lifetimeSpend;
        this.taxExempt = taxExempt;
        this.loyaltyPoints = loyaltyPoints;
        this.chargebackCount = chargebackCount;
        this.daysSinceLastLogin = daysSinceLastLogin;
        this.emailVerified = emailVerified;
        this.phoneVerified = phoneVerified;
        this.supportTickets = supportTickets;
        this.accountOpened = accountOpened;
    }

    public int computeRiskScore() {
        int score = 20;

        if (lifetimeSpend.compareTo(BigDecimal.valueOf(10_000)) >= 0) {
            score -= 10;
        }
        if (loyaltyPoints >= 500) {
            score -= 15;
        }
        if ("VIP".equals(tierCode)) {
            score -= 20;
        }
        if (taxExempt) {
            score += 5;
        }
        if (chargebackCount >= 2 || lifetimeSpend.compareTo(BigDecimal.valueOf(50)) < 0) {
            score += 25;
        }
        if (!emailVerified || !phoneVerified) {
            score += 15;
        }
        if (daysSinceLastLogin >= 90 && loyaltyPoints <= 50) {
            score += 20;
        }
        if ("HIGH".equals(homeRegion) && supportTickets >= 5) {
            score += 10;
        }
        long tenureDays = ChronoUnit.DAYS.between(accountOpened, LocalDate.now());
        if (tenureDays < 30 && lifetimeSpend.compareTo(BigDecimal.ZERO) == 0) {
            score += 12;
        }

        return Math.max(0, Math.min(100, score));
    }

    public String segment() {
        if ("VIP".equals(tierCode) || loyaltyPoints >= 1000) {
            return "VIP";
        }
        if (loyaltyPoints >= 200) {
            return "LOYAL";
        }
        return "STANDARD";
    }
}
