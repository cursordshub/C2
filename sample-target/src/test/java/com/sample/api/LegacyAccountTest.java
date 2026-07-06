package com.sample.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class LegacyAccountTest {

    // M2-generated infra (Mockito only — no PowerMock). M3 adds SUT coverage tests only.

    private LegacyAccount sut;

    @BeforeEach
    void setUp() {
        sut = new LegacyAccount("", 0, "");
    }

    @Test
    void infraIsReady() {
        assertNotNull(sut);
    }

}
