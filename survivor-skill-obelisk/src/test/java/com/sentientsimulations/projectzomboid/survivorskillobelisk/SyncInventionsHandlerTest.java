package com.sentientsimulations.projectzomboid.survivorskillobelisk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashMap;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import se.krka.kahlua.j2se.KahluaTableImpl;
import se.krka.kahlua.vm.KahluaTable;

/**
 * Tests for the table-mirroring core of {@link SyncInventionsHandler}: wholesale replacement
 * (client is authoritative), transient keys kept, junk and oversized payloads dropped.
 */
class SyncInventionsHandlerTest {

    private static final Supplier<KahluaTable> NEW_TABLE =
            () -> new KahluaTableImpl(new HashMap<>());

    private static KahluaTable table() {
        return NEW_TABLE.get();
    }

    private static KahluaTable entry(String name) {
        KahluaTable improvements = table();
        KahluaTable efficiency = table();
        efficiency.rawset(1.0, 2.0);
        efficiency.rawset(2.0, 9.0);
        improvements.rawset("efficiency", efficiency);
        KahluaTable entry = table();
        entry.rawset("invName", name);
        entry.rawset("improvementData", improvements);
        entry.rawset("numCreated", 1.0);
        return entry;
    }

    @Test
    void replacesTheMirrorWholesaleAndKeepsTransientKeys() {
        KahluaTable modData = table();
        KahluaTable stale = table();
        stale.rawset("Harvester", entry("Harvester"));
        modData.rawset("invData", stale);

        KahluaTable inventions = table();
        inventions.rawset("NeuralHat", entry("NeuralHat"));
        inventions.rawset("Hygienator", Boolean.FALSE);
        inventions.rawset("specialMax", 2.0);
        inventions.rawset("lsWkID", "wb-3");

        int mirrored = SyncInventionsHandler.mirrorInventions(inventions, modData, NEW_TABLE);

        assertEquals(1, mirrored);
        KahluaTable mirror = (KahluaTable) modData.rawget("invData");
        assertNull(mirror.rawget("Harvester"));
        assertEquals(Boolean.FALSE, mirror.rawget("Hygienator"));
        assertEquals(2.0, mirror.rawget("specialMax"));
        assertEquals("wb-3", mirror.rawget("lsWkID"));
        KahluaTable hat = (KahluaTable) mirror.rawget("NeuralHat");
        assertEquals("NeuralHat", hat.rawget("invName"));
        assertEquals(
                2.0,
                ((KahluaTable) ((KahluaTable) hat.rawget("improvementData")).rawget("efficiency"))
                        .rawget(1.0));
    }

    @Test
    void copiesRatherThanAliasesTheClientTable() {
        KahluaTable modData = table();
        KahluaTable inventions = table();
        KahluaTable hat = entry("NeuralHat");
        hat.rawset("evil", new Object());
        inventions.rawset("NeuralHat", hat);

        SyncInventionsHandler.mirrorInventions(inventions, modData, NEW_TABLE);

        KahluaTable mirror = (KahluaTable) modData.rawget("invData");
        KahluaTable mirroredHat = (KahluaTable) mirror.rawget("NeuralHat");
        assertNull(mirroredHat.rawget("evil"));
        hat.rawset("invName", "Tampered");
        assertEquals("NeuralHat", mirroredHat.rawget("invName"));
    }

    @Test
    void dropsAnOversizedPayloadWithoutTouchingTheMirror() {
        KahluaTable modData = table();
        KahluaTable existing = table();
        modData.rawset("invData", existing);
        KahluaTable inventions = table();
        for (int i = 0; i <= InventionsData.MAX_NODES; i++) {
            inventions.rawset("k" + i, 1.0);
        }

        int mirrored = SyncInventionsHandler.mirrorInventions(inventions, modData, NEW_TABLE);

        assertEquals(-1, mirrored);
        assertSame(existing, modData.rawget("invData"));
    }
}
