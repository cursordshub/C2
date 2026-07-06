package com.sample.api;

/**
 * Legacy account aggregate with package-private fields for reflection-based test stubs.
 */
public class LegacyAccount {

    private static final int EXPRESS_CREDIT_THRESHOLD = 750;

    private final String accountId;
    private final int publishedScore;
    String legacyToken;
    private int internalCreditScore;

    public LegacyAccount(String accountId, int publishedScore, String legacyToken) {
        this.accountId = accountId;
        this.publishedScore = publishedScore;
        this.legacyToken = legacyToken;
        this.internalCreditScore = publishedScore;
    }

    public String getAccountId() {
        return accountId;
    }

    public int getPublishedScore() {
        return publishedScore;
    }

    public boolean hasValidToken() {
        return legacyToken != null && !legacyToken.isBlank();
    }

    public boolean qualifiesForExpress() {
        return internalCreditScore >= EXPRESS_CREDIT_THRESHOLD && hasValidToken();
    }

    /**
     * Returns the internal credit score used for express eligibility checks.
     */
    int internalCreditScore() {
        return internalCreditScore;
    }

    /**
     * Returns true when the account id is non-blank.
     */
    public boolean hasAccountId() {
        return accountId != null && !accountId.isBlank();
    }

    /**
     * Returns true when the published score meets the express threshold.
     */
    public boolean meetsPublishedThreshold() {
        return publishedScore >= EXPRESS_CREDIT_THRESHOLD;
    }

    /**
     * Returns the credit gap to reach express eligibility, or zero when already eligible.
     */
    public int creditGapToExpress() {
        if (qualifiesForExpress()) {
            return 0;
        }
        return Math.max(0, EXPRESS_CREDIT_THRESHOLD - internalCreditScore);
    }

    /**
     * Returns a masked token suitable for audit logs.
     */
    public String maskedToken() {
        if (!hasValidToken()) {
            return "";
        }
        int visible = Math.min(4, legacyToken.length());
        return legacyToken.substring(0, visible) + "***";
    }

    /**
     * Returns true when both published and internal scores meet the express threshold.
     */
    public boolean isFullyQualifiedForExpress() {
        return meetsPublishedThreshold() && internalCreditScore >= EXPRESS_CREDIT_THRESHOLD && hasValidToken();
    }

    /**
     * Returns a diagnostic summary for support tooling.
     */
    public String supportSummary() {
        return "account=" + accountId + ",published=" + publishedScore + ",express=" + qualifiesForExpress();
    }
}
