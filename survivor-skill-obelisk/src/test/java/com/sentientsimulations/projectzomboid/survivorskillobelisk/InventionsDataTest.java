package com.sentientsimulations.projectzomboid.survivorskillobelisk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import se.krka.kahlua.j2se.KahluaTableImpl;
import se.krka.kahlua.vm.KahluaTable;

/**
 * Tests for the Lifestyles {@code invData} helpers: transient-key stripping, the depth and node
 * caps, and the never-lose-progress merge used by recovery.
 */
class InventionsDataTest {

    private static final Supplier<KahluaTable> NEW_TABLE =
            () -> new KahluaTableImpl(new HashMap<>());

    private static KahluaTable table() {
        return NEW_TABLE.get();
    }

    private static KahluaTable pair(double cur, double max) {
        KahluaTable t = table();
        t.rawset(1.0, cur);
        t.rawset(2.0, max);
        return t;
    }

    /** An invention entry with one improvement at {@code efficiency} and a production count. */
    private static KahluaTable entry(String name, double efficiency, double numCreated) {
        KahluaTable improvements = table();
        improvements.rawset("efficiency", pair(efficiency, 9.0));
        KahluaTable inventionData = table();
        inventionData.rawset("efficiency", 0.1 * (efficiency + 1));
        KahluaTable entry = table();
        entry.rawset("invName", name);
        entry.rawset("improvementData", improvements);
        entry.rawset("inventionData", inventionData);
        entry.rawset("numCreated", numCreated);
        entry.rawset("workCost", table());
        return entry;
    }

    private static double efficiency(KahluaTable entry) {
        return (Double)
                ((KahluaTable) ((KahluaTable) entry.rawget("improvementData")).rawget("efficiency"))
                        .rawget(1.0);
    }

    @Test
    void copyStripsOnlyTheTransientKeysWhenAsked() {
        KahluaTable invData = table();
        invData.rawset("lsWkID", "wb-7");
        invData.rawset("specialMax", 2.0);
        KahluaTable hat = entry("NeuralHat", 2.0, 1.0);
        KahluaTable inner = table();
        inner.rawset("workCost", "not transient this deep");
        hat.rawset("nested", inner);
        invData.rawset("NeuralHat", hat);

        KahluaTable stripped = InventionsData.copy(invData, NEW_TABLE, true);
        KahluaTable faithful = InventionsData.copy(invData, NEW_TABLE, false);

        assertNull(stripped.rawget("lsWkID"));
        assertEquals(2.0, stripped.rawget("specialMax"));
        KahluaTable strippedHat = (KahluaTable) stripped.rawget("NeuralHat");
        assertNull(strippedHat.rawget("workCost"));
        assertEquals(2.0, efficiency(strippedHat));
        assertEquals(
                "not transient this deep",
                ((KahluaTable) strippedHat.rawget("nested")).rawget("workCost"));
        assertEquals("wb-7", faithful.rawget("lsWkID"));
        assertNotNull(((KahluaTable) faithful.rawget("NeuralHat")).rawget("workCost"));
    }

    @Test
    void copyIsDeepAndDropsNonLuaValues() {
        KahluaTable invData = table();
        KahluaTable hat = entry("NeuralHat", 1.0, 1.0);
        hat.rawset("evil", new Object());
        hat.rawset(Boolean.TRUE, "boolean key");
        invData.rawset("NeuralHat", hat);

        KahluaTable copy = InventionsData.copy(invData, NEW_TABLE, false);
        KahluaTable copiedHat = (KahluaTable) copy.rawget("NeuralHat");

        assertNull(copiedHat.rawget("evil"));
        assertNull(copiedHat.rawget(Boolean.TRUE));
        assertEquals("NeuralHat", copiedHat.rawget("invName"));
        assertFalse(copiedHat.rawget("improvementData") == hat.rawget("improvementData"));
    }

    @Test
    void copyGivesUpPastTheDepthCap() {
        KahluaTable root = table();
        KahluaTable cursor = root;
        for (int i = 0; i <= InventionsData.MAX_DEPTH; i++) {
            KahluaTable next = table();
            cursor.rawset("n", next);
            cursor = next;
        }

        assertNull(InventionsData.copy(root, NEW_TABLE, false));
    }

    @Test
    void copyGivesUpPastTheNodeCap() {
        KahluaTable root = table();
        for (int i = 0; i <= InventionsData.MAX_NODES; i++) {
            root.rawset("k" + i, 1.0);
        }

        assertNull(InventionsData.copy(root, NEW_TABLE, false));
    }

    @Test
    void countEntriesAndHasProgressIgnoreScalarsAndForgottenMarkers() {
        KahluaTable seeded = table();
        seeded.rawset("specialMax", 1.0);
        KahluaTable forgotten = table();
        forgotten.rawset("specialMax", 1.0);
        forgotten.rawset("Hygienator", Boolean.FALSE);
        KahluaTable levelTen = table();
        levelTen.rawset("specialMax", 2.0);
        levelTen.rawset("specialMax_lvl10", Boolean.TRUE);
        KahluaTable researched = table();
        researched.rawset("NeuralHat", entry("NeuralHat", 0.0, 1.0));
        researched.rawset("Hygienator", Boolean.FALSE);

        assertEquals(0, InventionsData.countEntries(seeded));
        assertFalse(InventionsData.hasProgress(seeded));
        assertFalse(InventionsData.hasProgress(forgotten));
        assertTrue(InventionsData.hasProgress(levelTen));
        assertEquals(1, InventionsData.countEntries(researched));
        assertTrue(InventionsData.hasProgress(researched));
    }

    @Test
    void mergeRestoresMissingAndForgottenInventionsWithoutWorkCost() {
        KahluaTable saved = table();
        saved.rawset("NeuralHat", entry("NeuralHat", 3.0, 2.0));
        saved.rawset("Hygienator", entry("Hygienator", 0.0, 1.0));
        saved.rawset("lsWkID", "stale");
        KahluaTable live = table();
        live.rawset("Hygienator", Boolean.FALSE);

        assertTrue(InventionsData.merge(saved, live, NEW_TABLE));

        KahluaTable hat = (KahluaTable) live.rawget("NeuralHat");
        assertEquals(3.0, efficiency(hat));
        assertEquals(2.0, hat.rawget("numCreated"));
        assertNull(hat.rawget("workCost"));
        assertEquals("Hygienator", ((KahluaTable) live.rawget("Hygienator")).rawget("invName"));
        assertNull(live.rawget("lsWkID"));
    }

    @Test
    void mergeKeepsTheLiveEntryWhenItHasMoreResearchButRaisesNumCreated() {
        KahluaTable saved = table();
        saved.rawset("NeuralHat", entry("NeuralHat", 2.0, 10.0));
        KahluaTable live = table();
        KahluaTable liveHat = entry("NeuralHat", 5.0, 1.0);
        live.rawset("NeuralHat", liveHat);

        assertTrue(InventionsData.merge(saved, live, NEW_TABLE));

        assertSame(liveHat, live.rawget("NeuralHat"));
        assertEquals(5.0, efficiency(liveHat));
        assertEquals(10.0, liveHat.rawget("numCreated"));
    }

    @Test
    void mergeReplacesTheLiveEntryWhenSavedHasMoreResearchAndKeepsHigherNumCreated() {
        KahluaTable saved = table();
        saved.rawset("NeuralHat", entry("NeuralHat", 6.0, 1.0));
        KahluaTable live = table();
        live.rawset("NeuralHat", entry("NeuralHat", 1.0, 4.0));

        assertTrue(InventionsData.merge(saved, live, NEW_TABLE));

        KahluaTable hat = (KahluaTable) live.rawget("NeuralHat");
        assertEquals(6.0, efficiency(hat));
        assertEquals(
                0.7,
                (Double) ((KahluaTable) hat.rawget("inventionData")).rawget("efficiency"),
                1e-9);
        assertEquals(4.0, hat.rawget("numCreated"));
        assertNull(hat.rawget("workCost"));
    }

    @Test
    void mergeTakesMaxSpecialMaxAndOrsTheLevelTenFlag() {
        KahluaTable saved = table();
        saved.rawset("specialMax", 2.0);
        saved.rawset("specialMax_lvl10", Boolean.TRUE);
        KahluaTable live = table();
        live.rawset("specialMax", 1.0);

        assertTrue(InventionsData.merge(saved, live, NEW_TABLE));
        assertEquals(2.0, live.rawget("specialMax"));
        assertEquals(Boolean.TRUE, live.rawget("specialMax_lvl10"));

        KahluaTable lower = table();
        lower.rawset("specialMax", 1.0);
        lower.rawset("specialMax_lvl10", Boolean.FALSE);
        assertFalse(InventionsData.merge(lower, live, NEW_TABLE));
        assertEquals(2.0, live.rawget("specialMax"));
        assertEquals(Boolean.TRUE, live.rawget("specialMax_lvl10"));
    }

    @Test
    void mergeNeverUnlearnsAndIsIdempotent() {
        KahluaTable saved = table();
        saved.rawset("NeuralHat", Boolean.FALSE);
        saved.rawset("Harvester", entry("Harvester", 2.0, 3.0));
        KahluaTable live = table();
        live.rawset("NeuralHat", entry("NeuralHat", 1.0, 1.0));

        assertTrue(InventionsData.merge(saved, live, NEW_TABLE));
        assertEquals("NeuralHat", ((KahluaTable) live.rawget("NeuralHat")).rawget("invName"));
        assertEquals(2.0, efficiency((KahluaTable) live.rawget("Harvester")));

        assertFalse(InventionsData.merge(saved, live, NEW_TABLE));
    }

    @Test
    void progressScoreSumsCurrentImprovementLevelsAndToysScoreZero() {
        KahluaTable improvements = table();
        improvements.rawset("efficiency", pair(3.0, 9.0));
        improvements.rawset("durability", pair(2.0, 9.0));
        improvements.rawset("safe", pair(1.0, 1.0));
        KahluaTable invention = table();
        invention.rawset("improvementData", improvements);
        KahluaTable toy = table();
        toy.rawset("invName", "RubberDuck");

        assertEquals(6.0, InventionsData.progressScore(invention));
        assertEquals(0.0, InventionsData.progressScore(toy));
    }
}
