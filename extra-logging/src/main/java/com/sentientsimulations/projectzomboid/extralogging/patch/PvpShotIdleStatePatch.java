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
 * Client only. Logs the local player entering {@code IdleState} while the aiming flag is set,
 * because {@code IdleState.setParams} clears that flag and the next {@code updateAimingDelay} then
 * resets the aim delay.
 *
 * <p>Why a client patch: the state change is client-local and raises no Lua event. Fail-soft: the
 * advice suppresses every throwable, and a missing {@code enter} fails the weave, which leaves the
 * class vanilla. Re-validate on game updates: assumes {@code enter(IsoGameCharacter)} still calls
 * {@code setParams(Stage.Enter)}.
 */
public class PvpShotIdleStatePatch extends StormClassTransformer {

    static final String TARGET = "zombie.ai.states.IdleState";
    static final String ADVICE = PvpShotIdleStatePatch.class.getName() + "$EnterAdvice";

    public PvpShotIdleStatePatch() {
        super(TARGET);
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        ElementMatcher.Junction<MethodDescription> enter =
                ElementMatchers.named("enter").and(ElementMatchers.takesArguments(1));
        PatchTargets.require(typePool, TARGET, enter, "enter(IsoGameCharacter)");
        return builder.visit(Advice.to(typePool.describe(ADVICE).resolve(), locator).on(enter));
    }

    public static class EnterAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.Argument(0) Object owner) {
            PvpShotClientLog.onIdleEnter(owner);
        }
    }
}
