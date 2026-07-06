package com.sample.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CustomerAccountProfileTest {

    // M2-generated infra (Mockito only — no PowerMock). M3 adds SUT coverage tests only.

    private CustomerAccountProfile sut;

    @BeforeEach
    void setUp() {
        sut = buildCustomerAccountProfile();
    }

    /** Test-data builder — 12 fields (large fixture — populate all required test data here). */
    private CustomerAccountProfile buildCustomerAccountProfile() {
        return new CustomerAccountProfile(
                "CUST-1001",
                "PREMIUM",
                "US-NY",
                BigDecimal.valueOf(2500.00),
                false,
                720,
                1,
                14,
                true,
                true,
                2,
                LocalDate.now().minusYears(2)
        );
    }

    @Test
    void infraIsReady() {
        assertNotNull(sut);
    }

}
