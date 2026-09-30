package com.sentientsimulations.projectzomboid.extralogging.patch;

import com.sentientsimulations.projectzomboid.extralogging.pvphit.PvpShotClientLog;
import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

/**
 * Client only. Brackets {@code SwipeStatePlayer.doAttack} to read the aim delay at trigger pull and
 * again after {@code CombatManager.setAimingDelay} applies the post-shot penalty. The roll happens
 * later, in {@code attackCollisionCheck}, against the penalized value.
 *
 * <p>Why a client patch: the aim delay is client-only state with no Lua event at trigger pull.
 * Fail-soft: the advice suppresses every throwable, and a missing {@code doAttack} fails the weave,
 * which leaves the class vanilla. Re-validate on game updates: assumes {@code doAttack} still
 * applies the aim penalty.
 */
public class PvpShotDoAttackPatch extends StormClassTransformer {

    static final String TARGET = "zombie.ai.states.SwipeStatePlayer";
    static final String ADVICE = PvpShotDoAttackPatch.class.getName() + "$DoAttackAdvice";

    public PvpShotDoAttackPatch() {
        super(TARGET);
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        ElementMatcher.Junction<MethodDescription> doAttack =
                ElementMatchers.named("doAttack").and(ElementMatchers.takesArguments(4));
        PatchTargets.require(
                typePool, TARGET, doAttack, "doAttack(IsoPlayer, float, String, AttackVars)");
        return builder.visit(Advice.to(typePool.describe(ADVICE).resolve(), locator).on(doAttack));
    }

    public static class DoAttackAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.Argument(0) Object player) {
            PvpShotClientLog.beforeDoAttack(player);
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.Argument(0) Object player) {
            PvpShotClientLog.afterDoAttack(player);
        }
    }
}
