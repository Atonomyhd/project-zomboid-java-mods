package com.sentientsimulations.projectzomboid.survivorskillobelisk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ObeliskBlessingHandlerTest {

    @Test
    void grantedExpiryKeepsTheLongerRunningBlessing() {
        assertEquals(124.0, ObeliskBlessingHandler.extendedExpiry(100.0, 24.0, null), 1e-9);
        assertEquals(
                124.0,
                ObeliskBlessingHandler.extendedExpiry(
                        100.0, 24.0, new ObeliskBlessingHandler.Blessing(1L, "a", 110.0)),
                1e-9);
        assertEquals(
                130.0,
                ObeliskBlessingHandler.extendedExpiry(
                        100.0, 24.0, new ObeliskBlessingHandler.Blessing(1L, "a", 130.0)),
                1e-9);
    }

    @Test
    void grantRefusesNothingToGrant() {
        assertFalse(ObeliskBlessingHandler.grant(1L, "a", 0.0, "test"));
        assertFalse(ObeliskBlessingHandler.grant(1L, "a", -2.0, "test"));
        assertFalse(ObeliskBlessingHandler.grant(1L, "a", Double.NaN, "test"));
        assertFalse(ObeliskBlessingHandler.grant(1L, " ", 5.0, "test"));
        assertFalse(ObeliskBlessingHandler.grant(1L, null, 5.0, "test"));
    }
}
