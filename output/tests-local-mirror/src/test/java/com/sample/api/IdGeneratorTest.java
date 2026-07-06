package com.sample.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class IdGeneratorTest {

    // M2-generated infra (Mockito only — no PowerMock). M3 adds SUT coverage tests only.

    private IdGenerator sut;

    @BeforeEach
    void setUp() {
        sut = new IdGenerator();
    }

    @Test
    void infraIsReady() {
        assertNotNull(sut);
    }

}
