package com.sample.fulfillment;

import com.sample.api.ShippingPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class WarehouseDispatchRequestTest {

    // M2-generated infra (Mockito only — no PowerMock). M3 adds SUT coverage tests only.

    private WarehouseDispatchRequest sut;

    @BeforeEach
    void setUp() {
        sut = buildWarehouseDispatchRequest();
    }

    /** Test-data builder — 7 fields (medium fixture). */
    private WarehouseDispatchRequest buildWarehouseDispatchRequest() {
        return new WarehouseDispatchRequest(
                "ORD-9001",
                "WH-WEST-02",
                8,
                12.5,
                true,
                "FEDEX-2DAY",
                "fragile-electronics"
        );
    }

    @Test
    void infraIsReady() {
        assertNotNull(sut);
    }

}
