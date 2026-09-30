package com.sentientsimulations.projectzomboid.extralogging.pvphit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import zombie.network.GameServer;
import zombie.network.fields.hit.WeaponHit;
import zombie.network.packets.hit.PlayerHitPlayerPacket;

class PvpHitLogTest {

    @Test
    void sentFlagsSurviveALaterRewrite() {
        PlayerHitPlayerPacket packet = new PlayerHitPlayerPacket();
        List<WeaponHit> hits = PvpHitServerLog.hits(packet);
        hits.add(hit(true));
        hits.add(hit(false));

        boolean wasServer = GameServer.server;
        GameServer.server = true;
        try {
            PvpHitServerLog.afterParse(packet);
            hits.set(0, hit(false));
            PvpHitServerLog.Before before =
                    (PvpHitServerLog.Before) PvpHitServerLog.beforeProcess(packet);
            assertArrayEquals(new boolean[] {true, false}, before.ignoreDamageSent);
            assertEquals(Boolean.FALSE, PvpHitServerLog.ignoreDamage(hits.get(0)));
            PvpHitServerLog.onDamageFromWeapon(new Object());
            assertEquals(0, before.damageFromWeaponCalls);
        } finally {
            GameServer.server = wasServer;
        }
    }

    @Test
    void serverHooksIdleOffTheServer() {
        PlayerHitPlayerPacket packet = new PlayerHitPlayerPacket();
        assertFalse(GameServer.server);
        PvpHitServerLog.afterParse(packet);
        assertNull(PvpHitServerLog.beforeProcess(packet));
    }

    @Test
    void hitPartNamesMatchWeaponHitEncoding() {
        assertEquals("head", PvpHitServerLog.hitPartName((byte) 1));
        assertEquals("legs", PvpHitServerLog.hitPartName((byte) 2));
        assertEquals("other", PvpHitServerLog.hitPartName((byte) 4));
        assertEquals("unknown:9", PvpHitServerLog.hitPartName((byte) 9));
    }

    @Test
    void outcomeSeparatesRolledMissesFromHits() {
        assertEquals("notSent", PvpShotClientLog.outcome(null));
        assertEquals("hit", PvpShotClientLog.outcome(new int[] {1, 0}));
        assertEquals("missIgnoreDamage", PvpShotClientLog.outcome(new int[] {1, 1}));
        assertEquals("mixed", PvpShotClientLog.outcome(new int[] {3, 1}));
    }

    @Test
    void resetJumpNeedsARecentShotAndARealRise() {
        assertTrue(PvpShotClientLog.isResetJump(2.0F, 30.0F, 800L, true));
        assertFalse(PvpShotClientLog.isResetJump(30.0F, 30.0F, 800L, true));
        assertFalse(PvpShotClientLog.isResetJump(2.0F, 30.0F, 800L, false));
        assertFalse(
                PvpShotClientLog.isResetJump(
                        2.0F, 30.0F, PvpShotClientLog.RESET_WINDOW_MS + 1L, true));
    }

    @Test
    void clientHooksIdleOffTheClient() {
        assertTrue(Float.isNaN(PvpShotClientLog.beforeReset(new Object())));
        PvpShotClientLog.onIdleEnter(new Object());
        PvpShotClientLog.beforeCollision(new Object(), new Object());
        PvpShotClientLog.onClientHit(new Object(), true);
        PvpShotClientLog.afterCollision(new Object());
        assertEquals("none", PvpShotClientLog.targetType(null));
        assertEquals("Object", PvpShotClientLog.targetType(new Object()));
    }

    private static WeaponHit hit(boolean ignoreDamage) {
        WeaponHit hit = new WeaponHit();
        hit.set(1.0F, 2.0F, 0.0F, 0.0F, 0.0F, false, false, false, ignoreDamage);
        return hit;
    }
}
