package com.sentientsimulations.projectzomboid.extralogging.pvphit;

import java.util.LinkedHashMap;
import java.util.Map;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;

/** Target posture and movement flags shared by the server and client PvP hit logs. */
final class PvpHitStates {

    private PvpHitStates() {}

    static Map<String, Object> movement(IsoGameCharacter character) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("prone", character.isProne());
        out.put("onFloor", character.isOnFloor());
        out.put("knockedDown", character.isKnockedDown());
        out.put("moving", character instanceof IsoPlayer p ? p.isPlayerMoving() : null);
        out.put("running", character.isRunning());
        out.put("sprinting", character.isSprinting());
        out.put("sneaking", character.isSneaking());
        out.put("aiming", character.isAiming());
        return out;
    }
}
