package com.sentientsimulations.projectzomboid.survivorskillobelisk;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runtime knobs sourced from {@code media/sandbox-options.txt} (the {@code SkillObelisk.*}
 * options), pushed in by {@link SurvivorSkillObeliskSandboxApplier} at server start.
 *
 * <p>Each boolean toggles recovery of one of the tracked progression slices written by {@link
 * DeathEventHandler} — skills, recipes, read literature, read print media, watched recorded media,
 * Lifestyles instrument songs, Lifestyles ambitions, Lifestyles hidden skills (Yoga, Inventing,
 * PingPong), and Lifestyles invention research. {@code skillRecoveryPercent} scales the perk-level
 * / XP restore when {@code recoverSkills} is on; it has no effect when recovery is off. {@code
 * blessingDurationHours} and {@code blessingXpBonusPercent} shape the buff a Blessing-type obelisk
 * grants (see {@link ObeliskBlessingHandler}).
 *
 * <p>Values default to the constants below if the sandbox option is missing or fails to parse, so
 * unwired calls still return sane data in tests and on first launch before {@code OnServerStarted}
 * fires.
 */
public final class SurvivorSkillObeliskConfig {

    public static final boolean DEFAULT_RECOVER_SKILLS = true;
    public static final boolean DEFAULT_RECOVER_RECIPES = true;
    public static final boolean DEFAULT_RECOVER_SKILL_MAGAZINES = true;
    public static final boolean DEFAULT_RECOVER_READ_PRINT_MEDIA = true;
    public static final boolean DEFAULT_RECOVER_WATCHED_MEDIA = true;
    public static final boolean DEFAULT_RECOVER_LEARNED_SONGS = true;
    public static final boolean DEFAULT_RECOVER_AMBITIONS = true;
    public static final boolean DEFAULT_RECOVER_HIDDEN_SKILLS = true;
    public static final boolean DEFAULT_RECOVER_INVENTIONS = true;
    public static final int DEFAULT_SKILL_RECOVERY_PERCENT = 100;
    public static final boolean DEFAULT_CURSE_ON_SLEDGEHAMMER = true;
    public static final int DEFAULT_BLESSING_DURATION_HOURS = 24;
    public static final int DEFAULT_BLESSING_XP_BONUS_PERCENT = 50;
    public static final int MAX_BLESSING_DURATION_HOURS = 100000;
    public static final int MAX_BLESSING_XP_BONUS_PERCENT = 10000;

    private static final AtomicBoolean RECOVER_SKILLS = new AtomicBoolean(DEFAULT_RECOVER_SKILLS);
    private static final AtomicBoolean RECOVER_RECIPES = new AtomicBoolean(DEFAULT_RECOVER_RECIPES);
    private static final AtomicBoolean RECOVER_SKILL_MAGAZINES =
            new AtomicBoolean(DEFAULT_RECOVER_SKILL_MAGAZINES);
    private static final AtomicBoolean RECOVER_READ_PRINT_MEDIA =
            new AtomicBoolean(DEFAULT_RECOVER_READ_PRINT_MEDIA);
    private static final AtomicBoolean RECOVER_WATCHED_MEDIA =
            new AtomicBoolean(DEFAULT_RECOVER_WATCHED_MEDIA);
    private static final AtomicBoolean RECOVER_LEARNED_SONGS =
            new AtomicBoolean(DEFAULT_RECOVER_LEARNED_SONGS);
    private static final AtomicBoolean RECOVER_AMBITIONS =
            new AtomicBoolean(DEFAULT_RECOVER_AMBITIONS);
    private static final AtomicBoolean RECOVER_HIDDEN_SKILLS =
            new AtomicBoolean(DEFAULT_RECOVER_HIDDEN_SKILLS);
    private static final AtomicBoolean RECOVER_INVENTIONS =
            new AtomicBoolean(DEFAULT_RECOVER_INVENTIONS);
    private static final AtomicInteger SKILL_RECOVERY_PERCENT =
            new AtomicInteger(DEFAULT_SKILL_RECOVERY_PERCENT);
    private static final AtomicBoolean CURSE_ON_SLEDGEHAMMER =
            new AtomicBoolean(DEFAULT_CURSE_ON_SLEDGEHAMMER);
    private static final AtomicInteger BLESSING_DURATION_HOURS =
            new AtomicInteger(DEFAULT_BLESSING_DURATION_HOURS);
    private static final AtomicInteger BLESSING_XP_BONUS_PERCENT =
            new AtomicInteger(DEFAULT_BLESSING_XP_BONUS_PERCENT);

    private SurvivorSkillObeliskConfig() {}

    public static boolean isRecoverSkills() {
        return RECOVER_SKILLS.get();
    }

    public static void setRecoverSkills(boolean value) {
        RECOVER_SKILLS.set(value);
    }

    public static boolean isRecoverRecipes() {
        return RECOVER_RECIPES.get();
    }

    public static void setRecoverRecipes(boolean value) {
        RECOVER_RECIPES.set(value);
    }

    public static boolean isRecoverSkillMagazines() {
        return RECOVER_SKILL_MAGAZINES.get();
    }

    public static void setRecoverSkillMagazines(boolean value) {
        RECOVER_SKILL_MAGAZINES.set(value);
    }

    public static boolean isRecoverReadPrintMedia() {
        return RECOVER_READ_PRINT_MEDIA.get();
    }

    public static void setRecoverReadPrintMedia(boolean value) {
        RECOVER_READ_PRINT_MEDIA.set(value);
    }

    public static boolean isRecoverWatchedMedia() {
        return RECOVER_WATCHED_MEDIA.get();
    }

    public static void setRecoverWatchedMedia(boolean value) {
        RECOVER_WATCHED_MEDIA.set(value);
    }

    public static boolean isRecoverLearnedSongs() {
        return RECOVER_LEARNED_SONGS.get();
    }

    public static void setRecoverLearnedSongs(boolean value) {
        RECOVER_LEARNED_SONGS.set(value);
    }

    public static boolean isRecoverAmbitions() {
        return RECOVER_AMBITIONS.get();
    }

    public static void setRecoverAmbitions(boolean value) {
        RECOVER_AMBITIONS.set(value);
    }

    public static boolean isRecoverHiddenSkills() {
        return RECOVER_HIDDEN_SKILLS.get();
    }

    public static void setRecoverHiddenSkills(boolean value) {
        RECOVER_HIDDEN_SKILLS.set(value);
    }

    public static boolean isRecoverInventions() {
        return RECOVER_INVENTIONS.get();
    }

    public static void setRecoverInventions(boolean value) {
        RECOVER_INVENTIONS.set(value);
    }

    public static int getSkillRecoveryPercent() {
        return SKILL_RECOVERY_PERCENT.get();
    }

    /** Clamps to [0, 100] and stores. Returns the value actually applied. */
    public static int setSkillRecoveryPercent(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        SKILL_RECOVERY_PERCENT.set(clamped);
        return clamped;
    }

    public static boolean isCurseOnSledgehammer() {
        return CURSE_ON_SLEDGEHAMMER.get();
    }

    public static void setCurseOnSledgehammer(boolean value) {
        CURSE_ON_SLEDGEHAMMER.set(value);
    }

    /** In-game hours an obelisk blessing lasts from the moment it is received. */
    public static int getBlessingDurationHours() {
        return BLESSING_DURATION_HOURS.get();
    }

    /** Clamps to [1, MAX_BLESSING_DURATION_HOURS] and stores. Returns the value applied. */
    public static int setBlessingDurationHours(int hours) {
        int clamped = Math.max(1, Math.min(MAX_BLESSING_DURATION_HOURS, hours));
        BLESSING_DURATION_HOURS.set(clamped);
        return clamped;
    }

    /**
     * Extra XP a blessed character earns, as a percentage on top of the normal (sandbox-multiplied)
     * amount. 100 doubles every gain; values above 100 are allowed.
     */
    public static int getBlessingXpBonusPercent() {
        return BLESSING_XP_BONUS_PERCENT.get();
    }

    /** Clamps to [0, MAX_BLESSING_XP_BONUS_PERCENT] and stores. Returns the value applied. */
    public static int setBlessingXpBonusPercent(int percent) {
        int clamped = Math.max(0, Math.min(MAX_BLESSING_XP_BONUS_PERCENT, percent));
        BLESSING_XP_BONUS_PERCENT.set(clamped);
        return clamped;
    }

    /** Resets every option back to its compiled-in default. Test-only. */
    static void resetToDefaults() {
        RECOVER_SKILLS.set(DEFAULT_RECOVER_SKILLS);
        RECOVER_RECIPES.set(DEFAULT_RECOVER_RECIPES);
        RECOVER_SKILL_MAGAZINES.set(DEFAULT_RECOVER_SKILL_MAGAZINES);
        RECOVER_READ_PRINT_MEDIA.set(DEFAULT_RECOVER_READ_PRINT_MEDIA);
        RECOVER_WATCHED_MEDIA.set(DEFAULT_RECOVER_WATCHED_MEDIA);
        RECOVER_LEARNED_SONGS.set(DEFAULT_RECOVER_LEARNED_SONGS);
        RECOVER_AMBITIONS.set(DEFAULT_RECOVER_AMBITIONS);
        RECOVER_HIDDEN_SKILLS.set(DEFAULT_RECOVER_HIDDEN_SKILLS);
        RECOVER_INVENTIONS.set(DEFAULT_RECOVER_INVENTIONS);
        SKILL_RECOVERY_PERCENT.set(DEFAULT_SKILL_RECOVERY_PERCENT);
        CURSE_ON_SLEDGEHAMMER.set(DEFAULT_CURSE_ON_SLEDGEHAMMER);
        BLESSING_DURATION_HOURS.set(DEFAULT_BLESSING_DURATION_HOURS);
        BLESSING_XP_BONUS_PERCENT.set(DEFAULT_BLESSING_XP_BONUS_PERCENT);
    }
}
