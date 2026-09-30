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
 * Client only. Brackets {@code IsoGameCharacter.resetAimingDelay} to catch the aim delay jumping
 * back to the weapon's full aiming time shortly after a shot.
 *
 * <p>Why a client patch: the aim delay is client-only state. Fail-soft: the advice suppresses every
 * throwable, and a missing method fails the weave, which leaves the class vanilla. Re-validate on
 * game updates: assumes the reset still lives in {@code resetAimingDelay()}.
 */
public class PvpShotAimResetPatch extends StormClassTransformer {

    static final String TARGET = "zombie.characters.IsoGameCharacter";
    static final String ADVICE = PvpShotAimResetPatch.class.getName() + "$ResetAdvice";

    public PvpShotAimResetPatch() {
        super(TARGET);
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        ElementMatcher.Junction<MethodDescription> reset =
                ElementMatchers.named("resetAimingDelay").and(ElementMatchers.takesArguments(0));
        PatchTargets.require(typePool, TARGET, reset, "resetAimingDelay()");
        return builder.visit(Advice.to(typePool.describe(ADVICE).resolve(), locator).on(reset));
    }

    public static class ResetAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static float onEnter(@Advice.This Object self) {
            return PvpShotClientLog.beforeReset(self);
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.This Object self, @Advice.Enter float before) {
            PvpShotClientLog.afterReset(self, before);
        }
    }
}
