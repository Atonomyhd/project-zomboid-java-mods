package com.sentientsimulations.projectzomboid.survivorskillobelisk;

import io.pzstorm.storm.event.core.ClientCommand;
import io.pzstorm.storm.event.core.ClientCommandEvent;
import org.jetbrains.annotations.Nullable;
import se.krka.kahlua.vm.KahluaTable;
import zombie.characters.IsoPlayer;

/** Client asks for its own current blessing state; answered with {@code blessingState}. */
@ClientCommand(module = "SurvivorSkillObelisk", command = "getBlessing")
public class GetBlessingCommand extends ClientCommandEvent {

    public GetBlessingCommand(IsoPlayer player, @Nullable KahluaTable args) {
        super(player, args);
    }
}
