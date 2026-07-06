package com.sample.billing;

import com.sample.catalog.PaymentStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CheckoutResultTest {

    // M2-generated infra (Mockito only — no PowerMock). M3 adds SUT coverage tests only.

    private PaymentStatus paymentStatus;

    private CheckoutResult sut;

    @BeforeEach
    void setUp() {
        paymentStatus = PaymentStatus.STANDARD;
        sut = new CheckoutResult("", "", BigDecimal.ZERO, false, paymentStatus, "");
    }

    @Test
    void infraIsReady() {
        assertNotNull(sut);
    }

}
