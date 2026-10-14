package com.sentientsimulations.projectzomboid.survivorskillobelisk;

import io.pzstorm.storm.event.core.ClientCommand;
import io.pzstorm.storm.event.core.ClientCommandEvent;
import org.jetbrains.annotations.Nullable;
import se.krka.kahlua.vm.KahluaTable;
import zombie.characters.IsoPlayer;

@ClientCommand(module = "SurvivorSkillObelisk", command = "syncInventions")
public class SyncInventionsCommand extends ClientCommandEvent {

    public SyncInventionsCommand(IsoPlayer player, @Nullable KahluaTable args) {
        super(player, args);
    }

    /**
     * Lifestyles {@code invData} table — see {@link InventionsData}. {@code null} when the client
     * sent a malformed payload.
     */
    public @Nullable KahluaTable getInventions() {
        return rawget("inventions") instanceof KahluaTable inventions ? inventions : null;
    }
}
