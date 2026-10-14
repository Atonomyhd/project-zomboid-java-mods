package com.sentientsimulations.projectzomboid.survivorskillobelisk;

import java.util.function.Supplier;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaTableIterator;

/**
 * Shape helpers for Lifestyles' {@code player:getModData().invData}, the per-character table of
 * inventions and toys the character has researched (Lifestyles' InvWorkbenchMenu.lua and
 * LSIWAction.lua). Top-level keys are {@code specialMax} (number), {@code specialMax_lvl10}
 * (boolean), the transient workbench handle {@code lsWkID}, and one entry per invention keyed by
 * its name. An entry is a nested table ({@code level, invName, inventionData, improvementData,
 * numCreated, lastCreated, workCost, ...}) or {@code false} once the character has forgotten it.
 * {@code inventionData} is recomputed from {@code improvementData} every time research completes,
 * so {@code improvementData} is the progress that matters.
 */
final class InventionsData {

    static final String MOD_DATA_KEY = "invData";
    static final int MAX_DEPTH = 12;
    static final int MAX_NODES = 20_000;

    private InventionsData() {}

    /**
     * Deep-copy only what Lua itself can persist (strings, numbers, booleans, nested tables),
     * giving up at {@link #MAX_DEPTH} or {@link #MAX_NODES}. Returns null when a cap is hit so a
     * hostile payload is dropped whole rather than mirrored truncated. With {@code stripTransient},
     * the top-level workbench handle and each entry's cached material cost are left out: Lifestyles
     * regenerates both on the next workbench interaction, and a dead character's handle must not be
     * recovered onto a new one.
     */
    static KahluaTable copy(
            KahluaTable src, Supplier<KahluaTable> newTable, boolean stripTransient) {
        return copy(src, newTable, stripTransient, 0, new int[] {MAX_NODES});
    }

    /** {@link #copy} for a single invention entry, so the entry-level transient key is stripped. */
    static KahluaTable copyEntry(KahluaTable entry, Supplier<KahluaTable> newTable) {
        return copy(entry, newTable, true, 1, new int[] {MAX_NODES});
    }

    private static KahluaTable copy(
            KahluaTable src,
            Supplier<KahluaTable> newTable,
            boolean stripTransient,
            int depth,
            int[] budget) {
        if (depth > MAX_DEPTH) {
            return null;
        }
        KahluaTable out = newTable.get();
        KahluaTableIterator it = src.iterator();
        while (it.advance()) {
            Object key = it.getKey();
            Object value = it.getValue();
            if (!(key instanceof String) && !(key instanceof Double d && !d.isNaN())) {
                continue;
            }
            if (stripTransient && isTransientKey(key, depth)) {
                continue;
            }
            if (--budget[0] < 0) {
                return null;
            }
            if (value instanceof KahluaTable nested) {
                KahluaTable copied = copy(nested, newTable, stripTransient, depth + 1, budget);
                if (copied == null) {
                    return null;
                }
                out.rawset(key, copied);
            } else if (value instanceof String
                    || value instanceof Double
                    || value instanceof Boolean) {
                out.rawset(key, value);
            }
        }
        return out;
    }

    private static boolean isTransientKey(Object key, int depth) {
        return (depth == 0 && "lsWkID".equals(key)) || (depth == 1 && "workCost".equals(key));
    }

    /** Entries that are invention or toy tables, so not scalars or forgotten markers. */
    static int countEntries(KahluaTable invData) {
        int count = 0;
        KahluaTableIterator it = invData.iterator();
        while (it.advance()) {
            if (it.getKey() instanceof String && it.getValue() instanceof KahluaTable) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether the table holds anything worth a death row. Lifestyles seeds {@code specialMax = 1}
     * the first time any workbench menu opens, so that alone is not progress.
     */
    static boolean hasProgress(KahluaTable invData) {
        if (countEntries(invData) > 0) {
            return true;
        }
        if (Boolean.TRUE.equals(invData.rawget("specialMax_lvl10"))) {
            return true;
        }
        return invData.rawget("specialMax") instanceof Double max && max > 1;
    }

    /**
     * Merge a death snapshot into the live table. Progress is never taken away: scalars take the
     * max ({@code specialMax}) or the OR ({@code specialMax_lvl10}); an invention the live
     * character lacks or has forgotten is restored whole; one both hold keeps whichever side has
     * more research done (entries swap wholesale because {@code inventionData} is derived from
     * {@code improvementData}), with {@code numCreated} taking the max either way. Forgotten
     * markers in the snapshot never un-learn a live invention. Returns true when {@code live}
     * changed.
     */
    static boolean merge(KahluaTable saved, KahluaTable live, Supplier<KahluaTable> newTable) {
        boolean mutated = false;
        KahluaTableIterator it = saved.iterator();
        while (it.advance()) {
            if (!(it.getKey() instanceof String name) || isTransientKey(name, 0)) {
                continue;
            }
            Object value = it.getValue();
            switch (name) {
                case "specialMax" -> {
                    if (value instanceof Double savedMax) {
                        double liveMax = live.rawget(name) instanceof Double d ? d : 1.0;
                        if (savedMax > liveMax) {
                            live.rawset(name, savedMax);
                            mutated = true;
                        }
                    }
                }
                case "specialMax_lvl10" -> {
                    if (Boolean.TRUE.equals(value) && !Boolean.TRUE.equals(live.rawget(name))) {
                        live.rawset(name, Boolean.TRUE);
                        mutated = true;
                    }
                }
                default -> {
                    if (value instanceof KahluaTable savedEntry
                            && mergeEntry(name, savedEntry, live, newTable)) {
                        mutated = true;
                    }
                }
            }
        }
        return mutated;
    }

    private static boolean mergeEntry(
            String name, KahluaTable savedEntry, KahluaTable live, Supplier<KahluaTable> newTable) {
        double savedCreated = numCreated(savedEntry);
        if (!(live.rawget(name) instanceof KahluaTable liveEntry)) {
            KahluaTable copied = copyEntry(savedEntry, newTable);
            if (copied == null) {
                return false;
            }
            live.rawset(name, copied);
            return true;
        }
        double liveCreated = numCreated(liveEntry);
        if (progressScore(savedEntry) > progressScore(liveEntry)) {
            KahluaTable copied = copyEntry(savedEntry, newTable);
            if (copied == null) {
                return false;
            }
            if (liveCreated > savedCreated) {
                copied.rawset("numCreated", Double.valueOf(liveCreated));
            }
            live.rawset(name, copied);
            return true;
        }
        if (savedCreated > liveCreated) {
            liveEntry.rawset("numCreated", Double.valueOf(savedCreated));
            return true;
        }
        return false;
    }

    /** Total research done on an entry: the sum of current improvement levels. Toys score 0. */
    static double progressScore(KahluaTable entry) {
        if (!(entry.rawget("improvementData") instanceof KahluaTable improvements)) {
            return 0;
        }
        double score = 0;
        KahluaTableIterator it = improvements.iterator();
        while (it.advance()) {
            if (it.getValue() instanceof KahluaTable pair && pair.rawget(1) instanceof Double cur) {
                score += cur;
            }
        }
        return score;
    }

    private static double numCreated(KahluaTable entry) {
        return entry.rawget("numCreated") instanceof Double d ? d : 0;
    }
}
