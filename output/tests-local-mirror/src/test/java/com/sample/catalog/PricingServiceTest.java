package com.sample.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PricingServiceTest {

    // M2-generated infra (Mockito only — no PowerMock). M3 adds SUT coverage tests only.

    private PricingService sut;

    @BeforeEach
    void setUp() {
        sut = buildPricingService();
    }

    /** Test-data builder — 7 fields (medium fixture). */
    private PricingService buildPricingService() {
        return new PricingService(
                0.0,
                0.0,
                0.0,
                0.0,
                false,
                false
        );
    }

    @Test
    void infraIsReady() {
        assertNotNull(sut);
    }

}
