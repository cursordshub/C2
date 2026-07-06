package com.sample.fulfillment;

import com.sample.api.ShippingPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AllocationResultTest {

    // M2-generated infra (Mockito only — no PowerMock). M3 adds SUT coverage tests only.

    private ShippingPriority shippingPriority;

    private AllocationResult sut;

    @BeforeEach
    void setUp() {
        shippingPriority = ShippingPriority.STANDARD;
        sut = new AllocationResult("", "", 0, 0, shippingPriority, false);
    }

    @Test
    void infraIsReady() {
        assertNotNull(sut);
    }

}
