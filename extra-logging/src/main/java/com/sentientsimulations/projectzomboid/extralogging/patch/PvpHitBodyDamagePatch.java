package com.sentientsimulations.projectzomboid.extralogging.patch;

import com.sentientsimulations.projectzomboid.extralogging.pvphit.PvpHitServerLog;
import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

/**
 * Server only. Counts {@code BodyDamage.DamageFromWeapon} calls while a PvP hit packet is being
 * processed. {@code IsoPlayer.hitConsequences} skips this call when the hit carries {@code
 * ignoreDamage}.
 *
 * <p>Re-validate on game updates: assumes weapon body damage still enters through {@code
 * DamageFromWeapon(HandWeapon, int)}.
 */
public class PvpHitBodyDamagePatch extends StormClassTransformer {

    static final String TARGET = "zombie.characters.BodyDamage.BodyDamage";
    static final String ADVICE = PvpHitBodyDamagePatch.class.getName() + "$DamageFromWeaponAdvice";

    public PvpHitBodyDamagePatch() {
        super(TARGET);
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        ElementMatcher.Junction<MethodDescription> damage =
                ElementMatchers.named("DamageFromWeapon").and(ElementMatchers.takesArguments(2));
        PatchTargets.require(typePool, TARGET, damage, "DamageFromWeapon(HandWeapon, int)");
        return builder.visit(Advice.to(typePool.describe(ADVICE).resolve(), locator).on(damage));
    }

    public static class DamageFromWeaponAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This Object self) {
            PvpHitServerLog.onDamageFromWeapon(self);
        }
    }
}
