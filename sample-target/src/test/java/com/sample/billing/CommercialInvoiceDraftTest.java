package com.sample.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommercialInvoiceDraftTest {

    // M2-generated infra (Mockito only — no PowerMock). M3 adds SUT coverage tests only.

    private CommercialInvoiceDraft sut;

    @BeforeEach
    void setUp() {
        sut = buildCommercialInvoiceDraft();
    }

    /** Test-data builder — 12 fields (large fixture — populate all required test data here). */
    private CommercialInvoiceDraft buildCommercialInvoiceDraft() {
        return new CommercialInvoiceDraft(
                "INV-TEST-001",
                "Acme Buyer",
                "Sample Seller LLC",
                4,
                BigDecimal.valueOf(199.99),
                BigDecimal.valueOf(16.00),
                BigDecimal.valueOf(12.50),
                BigDecimal.ZERO,
                "USD",
                java.time.LocalDate.now(),
                java.time.LocalDate.now().plusDays(30),
                "NET30"
        );
    }

    @Test
    void infraIsReady() {
        assertNotNull(sut);
    }

}
