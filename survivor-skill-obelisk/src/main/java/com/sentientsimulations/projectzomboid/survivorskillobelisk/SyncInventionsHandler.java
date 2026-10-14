package com.sentientsimulations.projectzomboid.survivorskillobelisk;

import static io.pzstorm.storm.logging.StormLogger.LOGGER;

import io.pzstorm.storm.event.core.OnClientCommand;
import java.util.function.Supplier;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;

/**
 * Handles the {@code SurvivorSkillObelisk:syncInventions} client command. Mirrors Lifestyles'
 * {@code invData} invention-research table from the client onto the server-side {@link IsoPlayer}
 * modData.
 *
 * <p>Why this exists: same gap as {@link SyncHiddenSkillsHandler}, only wider. Lifestyles completes
 * research and production in client Lua (LSIWAction.lua) and never calls its own {@code
 * LS:SavePlayerData} mirror from there; only an Inventing level-up does. Everything researched
 * since the last level-up is invisible to {@link DeathEventHandler#snapshotInventions} at death.
 * The companion client script (SurvivorSkillObeliskInventionSync.lua) sends this command within a
 * game minute of any change.
 *
 * <p>Trust model matches the other syncs: this data is client-authoritative by design. The write is
 * confined to the {@code invData} key, only Lua-serialisable values are copied, and the copy is
 * bounded in depth and node count, so the packet can't plant arbitrary server-side state.
 *
 * <p>Runs on the main thread (client command dispatch): Kahlua tables are not thread-safe, and this
 * is a plain in-memory write with no I/O.
 */
public final class SyncInventionsHandler {

    private SyncInventionsHandler() {}

    @OnClientCommand
    public static void onSyncInventions(SyncInventionsCommand event) {
        IsoPlayer player = event.getPlayer();
        if (player == null) {
            LOGGER.warn("[SurvivorSkillObelisk] syncInventions from null player; dropping");
            return;
        }
        KahluaTable inventions = event.getInventions();
        if (inventions == null) {
            LOGGER.warn(
                    "[SurvivorSkillObelisk] syncInventions from {} with no inventions table;"
                            + " dropping",
                    player.getUsername());
            return;
        }
        KahluaTable modData = player.getModData();
        if (modData == null) {
            return;
        }
        int mirrored = mirrorInventions(inventions, modData, LuaManager.platform::newTable);
        if (mirrored < 0) {
            LOGGER.warn(
                    "[SurvivorSkillObelisk] syncInventions from {} exceeds the size cap; dropping",
                    player.getUsername());
            return;
        }
        LOGGER.debug(
                "[SurvivorSkillObelisk] syncInventions: mirrored {} inventions for {}",
                mirrored,
                player.getUsername());
    }

    /**
     * Replace {@code modData.invData} with a sanitized copy of the client-sent table. Replacement
     * (not merge) is deliberate and matches what Lifestyles' own mirror does: the client is the
     * source of truth, and a forgotten invention (stored as {@code false}) must overwrite the
     * server copy too. Transient keys are kept because Lifestyles reads the workbench handle back
     * after a relog to resume work in progress. Returns the number of invention entries written, or
     * -1 when the payload blew the depth or node cap and nothing was written.
     */
    static int mirrorInventions(
            KahluaTable inventions, KahluaTable modData, Supplier<KahluaTable> newTable) {
        KahluaTable mirror = InventionsData.copy(inventions, newTable, false);
        if (mirror == null) {
            return -1;
        }
        modData.rawset(InventionsData.MOD_DATA_KEY, mirror);
        return InventionsData.countEntries(mirror);
    }
}
