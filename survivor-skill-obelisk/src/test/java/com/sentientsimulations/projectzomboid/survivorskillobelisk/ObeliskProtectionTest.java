package com.sentientsimulations.projectzomboid.survivorskillobelisk;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentientsimulations.projectzomboid.survivorskillobelisk.patch.IsoThumpableGetThumpableForPatch;
import com.sentientsimulations.projectzomboid.survivorskillobelisk.patch.ObeliskProtection;
import com.sentientsimulations.projectzomboid.survivorskillobelisk.patch.SledgehammerDestroyPacketPatch;
import io.pzstorm.storm.core.StormClassTransformer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import zombie.network.packets.RemoveItemFromSquarePacket;
import zombie.network.packets.SledgehammerDestroyPacket;

class ObeliskProtectionTest {

    @Test
    void protectsAllObeliskSprites() {
        assertTrue(ObeliskProtection.isProtectedSpriteName("atf_obelisks_lg_01_0"));
        assertTrue(ObeliskProtection.isProtectedSpriteName("atf_obelisks_sm_01_13"));
        assertTrue(ObeliskProtection.isProtectedSpriteName("atf_obelisks_lg_01_mirror_7"));
        assertTrue(ObeliskProtection.isProtectedSpriteName("atf_obelisks_lg_01_on_0"));
    }

    @Test
    void ignoresOtherSprites() {
        assertFalse(ObeliskProtection.isProtectedSpriteName(null));
        assertFalse(ObeliskProtection.isProtectedSpriteName(""));
        assertFalse(ObeliskProtection.isProtectedSpriteName("walls_exterior_house_01_0"));
        assertFalse(ObeliskProtection.isProtectedSpriteName("atf_other_thing_0"));
    }

    @Test
    void sledgehammerPacketCarriesItsOwnTarget() {
        assertNotNull(ObeliskProtection.REMOVE_PACKET_Z_FIELD);
        assertTrue(
                RemoveItemFromSquarePacket.class.isAssignableFrom(SledgehammerDestroyPacket.class));
    }

    @Test
    void sledgehammerPatchWeaves() throws Exception {
        assertWeaves(new SledgehammerDestroyPacketPatch(), "shouldBlockSledgehammer");
    }

    @Test
    void thumpablePatchWeaves() throws Exception {
        assertWeaves(new IsoThumpableGetThumpableForPatch(), "isProtectedObject");
    }

    private static void assertWeaves(StormClassTransformer patch, String guard) throws Exception {
        String resource = "/" + patch.getClassName().replace('.', '/') + ".class";
        byte[] raw;
        try (InputStream in = ObeliskProtectionTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " not on test classpath");
            raw = in.readAllBytes();
        }
        byte[] woven = patch.transform(raw);
        assertNotEquals(raw.length, woven.length, "advice was not woven in");
        assertTrue(
                new String(woven, StandardCharsets.ISO_8859_1).contains(guard),
                "woven class does not call " + guard);
    }
}
