package com.sentientsimulations.projectzomboid.survivorskillobelisk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Pure-math tests for the blessing bonus; the AddXP plumbing needs a live server. */
class ObeliskBlessingXpBoostTest {

    @Test
    void bonusIsPercentOfBaseAmount() {
        assertEquals(5f, ObeliskBlessingXpBoost.computeBonus(10f, 50), 1e-6f);
        assertEquals(10f, ObeliskBlessingXpBoost.computeBonus(10f, 100), 1e-6f);
    }

    @Test
    void bonusAbove100PercentIsAllowed() {
        assertEquals(30f, ObeliskBlessingXpBoost.computeBonus(10f, 300), 1e-6f);
    }

    @Test
    void zeroOrNegativeInputsYieldNoBonus() {
        assertEquals(0f, ObeliskBlessingXpBoost.computeBonus(10f, 0), 0f);
        assertEquals(0f, ObeliskBlessingXpBoost.computeBonus(0f, 50), 0f);
        assertEquals(0f, ObeliskBlessingXpBoost.computeBonus(-4f, 50), 0f);
        assertEquals(0f, ObeliskBlessingXpBoost.computeBonus(Float.NaN, 50), 0f);
    }
}
