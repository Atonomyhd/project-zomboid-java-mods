package com.sentientsimulations.projectzomboid.survivorskillobelisk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SurvivorSkillObeliskConfigTest {

    @BeforeEach
    void resetBefore() {
        SurvivorSkillObeliskConfig.resetToDefaults();
    }

    @AfterEach
    void resetAfter() {
        SurvivorSkillObeliskConfig.resetToDefaults();
    }

    @Test
    void defaultsMatchDeclaredConstants() {
        assertTrue(SurvivorSkillObeliskConfig.DEFAULT_RECOVER_SKILLS);
        assertTrue(SurvivorSkillObeliskConfig.isRecoverSkills());
        assertTrue(SurvivorSkillObeliskConfig.isRecoverRecipes());
        assertTrue(SurvivorSkillObeliskConfig.isRecoverSkillMagazines());
        assertTrue(SurvivorSkillObeliskConfig.isRecoverReadPrintMedia());
        assertTrue(SurvivorSkillObeliskConfig.isRecoverWatchedMedia());
        assertTrue(SurvivorSkillObeliskConfig.isRecoverLearnedSongs());
        assertTrue(SurvivorSkillObeliskConfig.isRecoverAmbitions());
        assertTrue(SurvivorSkillObeliskConfig.isRecoverHiddenSkills());
        assertTrue(SurvivorSkillObeliskConfig.isRecoverInventions());
        assertEquals(100, SurvivorSkillObeliskConfig.DEFAULT_SKILL_RECOVERY_PERCENT);
        assertEquals(100, SurvivorSkillObeliskConfig.getSkillRecoveryPercent());
        assertTrue(SurvivorSkillObeliskConfig.DEFAULT_CURSE_ON_SLEDGEHAMMER);
        assertTrue(SurvivorSkillObeliskConfig.isCurseOnSledgehammer());
        assertEquals(24, SurvivorSkillObeliskConfig.DEFAULT_BLESSING_DURATION_HOURS);
        assertEquals(24, SurvivorSkillObeliskConfig.getBlessingDurationHours());
        assertEquals(50, SurvivorSkillObeliskConfig.DEFAULT_BLESSING_XP_BONUS_PERCENT);
        assertEquals(50, SurvivorSkillObeliskConfig.getBlessingXpBonusPercent());
    }

    @Test
    void blessingDurationClampsToAtLeastOneHour() {
        assertEquals(1, SurvivorSkillObeliskConfig.setBlessingDurationHours(0));
        assertEquals(1, SurvivorSkillObeliskConfig.setBlessingDurationHours(-5));
        assertEquals(1, SurvivorSkillObeliskConfig.getBlessingDurationHours());
        assertEquals(
                SurvivorSkillObeliskConfig.MAX_BLESSING_DURATION_HOURS,
                SurvivorSkillObeliskConfig.setBlessingDurationHours(Integer.MAX_VALUE));
        assertEquals(72, SurvivorSkillObeliskConfig.setBlessingDurationHours(72));
    }

    @Test
    void blessingXpBonusAllowsValuesAbove100() {
        assertEquals(0, SurvivorSkillObeliskConfig.setBlessingXpBonusPercent(-1));
        assertEquals(300, SurvivorSkillObeliskConfig.setBlessingXpBonusPercent(300));
        assertEquals(300, SurvivorSkillObeliskConfig.getBlessingXpBonusPercent());
        assertEquals(
                SurvivorSkillObeliskConfig.MAX_BLESSING_XP_BONUS_PERCENT,
                SurvivorSkillObeliskConfig.setBlessingXpBonusPercent(Integer.MAX_VALUE));
    }

    @Test
    void setterRoundTripsEachBoolean() {
        SurvivorSkillObeliskConfig.setRecoverSkills(false);
        SurvivorSkillObeliskConfig.setRecoverRecipes(false);
        SurvivorSkillObeliskConfig.setRecoverSkillMagazines(false);
        SurvivorSkillObeliskConfig.setRecoverReadPrintMedia(false);
        SurvivorSkillObeliskConfig.setRecoverWatchedMedia(false);
        SurvivorSkillObeliskConfig.setRecoverLearnedSongs(false);
        SurvivorSkillObeliskConfig.setRecoverAmbitions(false);
        SurvivorSkillObeliskConfig.setRecoverHiddenSkills(false);
        SurvivorSkillObeliskConfig.setRecoverInventions(false);
        SurvivorSkillObeliskConfig.setCurseOnSledgehammer(false);

        assertFalse(SurvivorSkillObeliskConfig.isRecoverSkills());
        assertFalse(SurvivorSkillObeliskConfig.isRecoverRecipes());
        assertFalse(SurvivorSkillObeliskConfig.isRecoverSkillMagazines());
        assertFalse(SurvivorSkillObeliskConfig.isRecoverReadPrintMedia());
        assertFalse(SurvivorSkillObeliskConfig.isRecoverWatchedMedia());
        assertFalse(SurvivorSkillObeliskConfig.isRecoverLearnedSongs());
        assertFalse(SurvivorSkillObeliskConfig.isRecoverAmbitions());
        assertFalse(SurvivorSkillObeliskConfig.isRecoverHiddenSkills());
        assertFalse(SurvivorSkillObeliskConfig.isRecoverInventions());
        assertFalse(SurvivorSkillObeliskConfig.isCurseOnSledgehammer());

        SurvivorSkillObeliskConfig.setRecoverSkills(true);
        assertTrue(SurvivorSkillObeliskConfig.isRecoverSkills());
    }

    @Test
    void skillRecoveryPercentClampsToZeroLowerBound() {
        assertEquals(0, SurvivorSkillObeliskConfig.setSkillRecoveryPercent(-5));
        assertEquals(0, SurvivorSkillObeliskConfig.getSkillRecoveryPercent());
        assertEquals(0, SurvivorSkillObeliskConfig.setSkillRecoveryPercent(Integer.MIN_VALUE));
        assertEquals(0, SurvivorSkillObeliskConfig.getSkillRecoveryPercent());
    }

    @Test
    void skillRecoveryPercentClampsTo100UpperBound() {
        assertEquals(100, SurvivorSkillObeliskConfig.setSkillRecoveryPercent(150));
        assertEquals(100, SurvivorSkillObeliskConfig.getSkillRecoveryPercent());
        assertEquals(100, SurvivorSkillObeliskConfig.setSkillRecoveryPercent(Integer.MAX_VALUE));
        assertEquals(100, SurvivorSkillObeliskConfig.getSkillRecoveryPercent());
    }

    @Test
    void skillRecoveryPercentAcceptsInRangeValues() {
        assertEquals(0, SurvivorSkillObeliskConfig.setSkillRecoveryPercent(0));
        assertEquals(0, SurvivorSkillObeliskConfig.getSkillRecoveryPercent());
        assertEquals(50, SurvivorSkillObeliskConfig.setSkillRecoveryPercent(50));
        assertEquals(50, SurvivorSkillObeliskConfig.getSkillRecoveryPercent());
        assertEquals(100, SurvivorSkillObeliskConfig.setSkillRecoveryPercent(100));
        assertEquals(100, SurvivorSkillObeliskConfig.getSkillRecoveryPercent());
    }
}
