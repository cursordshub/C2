package com.sample.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Hand-written collision-detection fixture — M2 must not overwrite this file.
 */
class CustomerTest {

    private Customer sut;

    @BeforeEach
    void setUp() {
        sut = new Customer("C-100", "Ada Lovelace");
    }

    @Test
    void exposesIdentityFields() {
        assertNotNull(sut);
        assertEquals("C-100", sut.getId());
        assertEquals("Ada Lovelace", sut.getName());
    }
}
