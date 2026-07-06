package com.sample.catalog;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CustomerTest {

    // M2-generated infra (Mockito only — no PowerMock). M3 adds SUT coverage tests only.

    private Customer sut;

    @BeforeEach
    void setUp() {
        sut = buildCustomer();
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

    @Test
    void infraIsReady() {
        assertNotNull(sut);
    }

}
