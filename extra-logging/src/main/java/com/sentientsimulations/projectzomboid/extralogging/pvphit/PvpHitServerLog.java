package com.sentientsimulations.projectzomboid.extralogging.pvphit;

import static io.pzstorm.storm.logging.StormLogger.LOGGER;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentientsimulations.projectzomboid.extralogging.ExtraLoggerFactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.slf4j.Logger;
import zombie.SandboxOptions;
import zombie.characters.BodyDamage.BodyDamage;
import zombie.characters.BodyDamage.BodyPart;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.inventory.types.HandWeapon;
import zombie.network.GameServer;
import zombie.network.fields.hit.WeaponHit;
import zombie.network.packets.hit.PlayerHitCharacter;
import zombie.network.packets.hit.PlayerHitPlayerPacket;

/**
 * Server half of the PvP hit audit. Writes one JSON line per processed {@code
 * PlayerHitPlayerPacket} to {@code extra-logging/pvp-hits.json}:
 *
 * <ul>
 *   <li>{@code ignoreDamageSent} is the flag as it came off the wire. It is captured when {@code
 *       PlayerHitCharacter.parse} returns, before any mod's {@code PlayerHitPlayerPacket.parse}
 *       exit advice can rewrite it.
 *   <li>{@code ignoreDamageApplied} is the flag {@code process()} actually hands to {@code
 *       IsoGameCharacter.Hit}.
 *   <li>{@code damageFromWeaponCalls} counts {@code BodyDamage.DamageFromWeapon} calls on the
 *       target during {@code process()}. Zero means the server skipped body damage.
 *   <li>{@code partHealthBefore}/{@code partHealthAfter} sum the target's body part health. {@code
 *       overallBodyHealth} is only recomputed in the body damage update, so it lags a tick.
 * </ul>
 *
 * <p>A shot the client rolled as a miss under {@code FirearmUseDamageChance} 1 or 2 never reaches
 * the server, so it has no line here. The client log records it.
 *
 * <p>Methods take {@code Object} so the inlined advice carries no game-class checkcasts.
 */
public final class PvpHitServerLog {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final VarHandle HITS;
    private static final VarHandle IGNORE_DAMAGE;
    private static final VarHandle RANGE;
    private static final VarHandle HIT_PART;

    static {
        VarHandle hits = null;
        VarHandle ignoreDamage = null;
        VarHandle range = null;
        VarHandle hitPart = null;
        try {
            MethodHandles.Lookup packet =
                    MethodHandles.privateLookupIn(PlayerHitCharacter.class, MethodHandles.lookup());
            hits = packet.findVarHandle(PlayerHitCharacter.class, "hits", List.class);
            MethodHandles.Lookup hit =
                    MethodHandles.privateLookupIn(WeaponHit.class, MethodHandles.lookup());
            ignoreDamage = hit.findVarHandle(WeaponHit.class, "ignoreDamage", boolean.class);
            range = hit.findVarHandle(WeaponHit.class, "range", float.class);
            hitPart = hit.findVarHandle(WeaponHit.class, "hitPart", byte.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("extra-logging: PvP hit audit cannot read hit fields", e);
        }
        HITS = hits;
        IGNORE_DAMAGE = ignoreDamage;
        RANGE = range;
        HIT_PART = hitPart;
    }

    private static final Map<PlayerHitPlayerPacket, boolean[]> SENT_FLAGS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final ThreadLocal<Before> PROCESSING = new ThreadLocal<>();

    /** Taken at {@code process()} entry and threaded to the exit advice. */
    public static final class Before {
        final long nanos = System.nanoTime();
        final IsoGameCharacter target;
        final boolean[] ignoreDamageSent;
        final float partHealth;
        final float overallBodyHealth;
        int damageFromWeaponCalls;

        Before(IsoGameCharacter target, boolean[] ignoreDamageSent) {
            this.target = target;
            this.ignoreDamageSent = ignoreDamageSent;
            this.partHealth = partHealth(target);
            this.overallBodyHealth = overallBodyHealth(target);
        }
    }

    private static final class Out {
        static final Logger LOG = ExtraLoggerFactory.createLogger("pvp-hits", "json", 5);
    }

    private PvpHitServerLog() {}

    public static void afterParse(Object packetObj) {
        if (GameServer.server && packetObj instanceof PlayerHitPlayerPacket packet) {
            SENT_FLAGS.put(packet, ignoreFlags(hits(packet)));
        }
    }

    public static Object beforeProcess(Object packetObj) {
        if (!GameServer.server || !(packetObj instanceof PlayerHitPlayerPacket packet)) {
            return null;
        }
        Before before = new Before(packet.getTarget(), SENT_FLAGS.remove(packet));
        PROCESSING.set(before);
        return before;
    }

    public static void onDamageFromWeapon(Object bodyDamageObj) {
        Before before = PROCESSING.get();
        if (before != null
                && bodyDamageObj instanceof BodyDamage bodyDamage
                && before.target != null
                && bodyDamage.getParentChar() == before.target) {
            before.damageFromWeaponCalls++;
        }
    }

    public static void afterProcess(Object packetObj, Object beforeObj) {
        if (!(beforeObj instanceof Before before)) {
            return;
        }
        PROCESSING.remove();
        if (!(packetObj instanceof PlayerHitPlayerPacket packet)) {
            return;
        }
        try {
            Out.LOG.info(MAPPER.writeValueAsString(record(packet, before)));
        } catch (Exception e) {
            LOGGER.warn("extra-logging: PvP hit audit write failed", e);
        }
    }

    static Map<String, Object> record(PlayerHitPlayerPacket packet, Before before) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ts", System.currentTimeMillis());
        IsoPlayer shooter = packet.getWielder();
        IsoGameCharacter target = before.target;
        out.put("shooter", shooter == null ? null : shooter.getUsername());
        out.put("target", target instanceof IsoPlayer p ? p.getUsername() : null);
        HandWeapon weapon = packet.getHandWeapon();
        out.put("weapon", weapon == null ? null : weapon.getFullType());
        out.put("aimedFirearm", weapon != null && weapon.isAimedFirearm());
        out.put("distance", packet.getDistance());
        out.put(
                "firearmUseDamageChance",
                SandboxOptions.instance.firearmUseDamageChance.getValue());
        out.put("shooterAimAtFloor", shooter != null && shooter.isAimAtFloor());
        if (target != null) {
            out.put("targetState", PvpHitStates.movement(target));
        }

        List<WeaponHit> hits = hits(packet);
        List<Map<String, Object>> hitList = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            WeaponHit hit = hits.get(i);
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("damage", hit.getDamage());
            h.put("range", RANGE == null ? null : (float) RANGE.get(hit));
            h.put("part", HIT_PART == null ? null : hitPartName((byte) HIT_PART.get(hit)));
            boolean[] sent = before.ignoreDamageSent;
            h.put("ignoreDamageSent", sent != null && i < sent.length ? sent[i] : null);
            h.put("ignoreDamageApplied", ignoreDamage(hit));
            hitList.add(h);
        }
        out.put("hits", hitList);
        out.put("damageFromWeaponCalls", before.damageFromWeaponCalls);
        float partAfter = partHealth(target);
        out.put("partHealthBefore", before.partHealth);
        out.put("partHealthAfter", partAfter);
        out.put("partHealthDelta", partAfter - before.partHealth);
        out.put("overallBodyHealthBefore", before.overallBodyHealth);
        out.put("targetDead", target != null && target.isDead());
        out.put("processMicros", (System.nanoTime() - before.nanos) / 1000L);
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<WeaponHit> hits(PlayerHitCharacter packet) {
        if (HITS == null) {
            return List.of();
        }
        List<WeaponHit> hits = (List<WeaponHit>) HITS.get(packet);
        return hits == null ? List.of() : hits;
    }

    static boolean[] ignoreFlags(List<WeaponHit> hits) {
        boolean[] flags = new boolean[hits.size()];
        for (int i = 0; i < flags.length; i++) {
            Boolean flag = ignoreDamage(hits.get(i));
            flags[i] = flag != null && flag;
        }
        return flags;
    }

    static Boolean ignoreDamage(WeaponHit hit) {
        return IGNORE_DAMAGE == null ? null : (boolean) IGNORE_DAMAGE.get(hit);
    }

    static String hitPartName(byte part) {
        return switch (part) {
            case 1 -> "head";
            case 2 -> "legs";
            case 4 -> "other";
            default -> "unknown:" + part;
        };
    }

    static float partHealth(IsoGameCharacter character) {
        if (character == null || character.getBodyDamage() == null) {
            return Float.NaN;
        }
        float sum = 0.0F;
        for (BodyPart part : character.getBodyDamage().getBodyParts()) {
            sum += part.getHealth();
        }
        return sum;
    }

    static float overallBodyHealth(IsoGameCharacter character) {
        return character == null || character.getBodyDamage() == null
                ? Float.NaN
                : character.getBodyDamage().getOverallBodyHealth();
    }
}
