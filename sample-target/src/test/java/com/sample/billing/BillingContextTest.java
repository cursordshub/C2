package com.sample.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class BillingContextTest {

    // M2-generated infra (Mockito only — no PowerMock). M3 adds SUT coverage tests only.

    private BillingContext sut;

    @BeforeEach
    void setUp() {
        sut = buildBillingContext();
    }

    /** Test-data builder — 6 fields (medium fixture). */
    private BillingContext buildBillingContext() {
        return new BillingContext(
                "US-CA",
                "USD",
                3,
                15_000,
                true,
                "MOBILE"
        );
    }

    @Test
    void infraIsReady() {
        assertNotNull(sut);
    }

}
