package com.sentientsimulations.projectzomboid.survivorskillobelisk;

import static io.pzstorm.storm.logging.StormLogger.LOGGER;

import io.pzstorm.storm.event.core.SubscribeEvent;
import io.pzstorm.storm.event.lua.AddXPEvent;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.NetworkCharacterAI;
import zombie.characters.skills.PerkFactory;
import zombie.network.GameServer;

/**
 * Applies the Obelisk Blessing XP bonus on the server. Every {@code XP.AddXP} on the dedicated
 * server fires the Lua {@code AddXP} event (bridged to {@link AddXPEvent}) with the final,
 * sandbox-multiplied amount, so a second {@code AddXP} of {@code amount * bonus%} on top gives
 * "bonus% above the normal XP rate" without touching the multiplier maps. The follow-up call runs
 * with {@code doXPBoost=false} so sandbox and trait multipliers are not applied twice, and with
 * {@code callLua=false}; the event it fires is ignored via the re-entrancy guard.
 *
 * <p>Clients learn of the extra XP through the normal {@code PlayerXp} sync, which is why this
 * lives on the server rather than in Lua: the MP client's {@code addXp} is a no-op.
 */
public final class ObeliskBlessingXpBoost {

    private static final ThreadLocal<Boolean> SUPPRESSED = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> IN_BONUS = ThreadLocal.withInitial(() -> false);

    private ObeliskBlessingXpBoost() {}

    /** Runs {@code action} with blessing boosting disabled, e.g. for XP restores. */
    public static void runUnboosted(Runnable action) {
        boolean previous = SUPPRESSED.get();
        SUPPRESSED.set(true);
        try {
            action.run();
        } finally {
            SUPPRESSED.set(previous);
        }
    }

    @SubscribeEvent
    public static void onAddXp(AddXPEvent event) {
        if (!GameServer.server || SUPPRESSED.get() || IN_BONUS.get()) {
            return;
        }
        if (!(event.player instanceof IsoPlayer player) || event.perk == null) {
            return;
        }
        Float amount = event.xpAmount;
        if (amount == null || !(amount > 0f)) {
            return;
        }
        ObeliskBlessingHandler.Blessing blessing = ObeliskBlessingHandler.activeBlessing(player);
        if (blessing == null) {
            return;
        }
        float bonus =
                computeBonus(amount, SurvivorSkillObeliskConfig.getBlessingXpBonusPercent())
                        / proteinFactor(player, event.perk);
        if (!(bonus > 0f)) {
            return;
        }
        IN_BONUS.set(true);
        try {
            player.getXp().AddXP(event.perk, bonus, false, false, false, false);
            NetworkCharacterAI ai = player.getNetworkCharacterAI();
            if (ai != null) {
                ai.updateXpChecker();
            }
        } catch (Throwable t) {
            LOGGER.warn(
                    "[SurvivorSkillObelisk] blessing bonus failed for {} / {}: {}",
                    player.getUsername(),
                    event.perk.getId(),
                    t.getMessage());
        } finally {
            IN_BONUS.set(false);
        }
    }

    /** Pure math: the extra XP owed on top of {@code baseAmount} for {@code bonusPercent}. */
    static float computeBonus(float baseAmount, int bonusPercent) {
        if (bonusPercent <= 0 || !(baseAmount > 0f)) {
            return 0f;
        }
        return baseAmount * (bonusPercent / 100f);
    }

    /**
     * Vanilla scales Strength XP by nutrition before any multiplier, and does so on the bonus call
     * too. Dividing the bonus by the same factor keeps it an exact percentage of the base gain.
     */
    static float proteinFactor(IsoGameCharacter chr, PerkFactory.Perk perk) {
        if (perk.getType() != PerkFactory.Perks.Strength || !(chr instanceof IsoPlayer player)) {
            return 1f;
        }
        float proteins = player.getNutrition().getProteins();
        float factor = 1f;
        if (proteins > 50f && proteins < 300f) {
            factor *= 1.5f;
        }
        if (proteins < -300f) {
            factor *= 0.7f;
        }
        return factor;
    }
}
