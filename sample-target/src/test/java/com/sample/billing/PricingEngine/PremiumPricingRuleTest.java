package com.sample.billing.PricingEngine;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PremiumPricingRuleTest {

    // M2-generated infra (Mockito only — no PowerMock). M3 adds SUT coverage tests only.

    private PremiumPricingRule sut;

    @BeforeEach
    void setUp() {
        sut = new PremiumPricingRule();
    }

    @Test
    void infraIsReady() {
        assertNotNull(sut);
    }

}
