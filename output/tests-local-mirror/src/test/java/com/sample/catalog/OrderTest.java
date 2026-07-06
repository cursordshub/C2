package com.sample.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class OrderTest {

    // M2-generated infra (Mockito only — no PowerMock). M3 adds SUT coverage tests only.

    private Customer customer;

    private PricingService pricingService;

    private Order sut;

    @BeforeEach
    void setUp() {
        customer = buildCustomer();
        pricingService = buildPricingService();
        sut = new Order("", customer, pricingService);
    }

    /** Test-data builder — 10 fields (large fixture — populate all required test data here). */
    private Customer buildCustomer() {
        return new Customer(
                "test-id",
                "test-name",
                "test-email",
                "test-regionCode",
                5,
                false,
                LocalDate.now(),
                "test-preferredCurrency",
                9,
                "test-referralCode"
        );
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
