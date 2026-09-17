package com.sentientsimulations.projectzomboid.extralogging.patch;

import com.sentientsimulations.projectzomboid.extralogging.animal.AnimalFatalHealthDrop;
import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

/**
 * Wraps {@code IsoAnimal.setHealth(float)} so {@link AnimalFatalHealthDrop} sees every health write
 * and stores the calling path of the first fatal one. Observation only; the health write is
 * untouched. Sits beside Storm's own {@code AnimalDeathBypassPatch} seams on the same class.
 *
 * <p>Re-validate on game updates: assumes {@code setHealth(float)} stays the single override every
 * animal health drain routes through.
 */
public class IsoAnimalPatch extends StormClassTransformer {

    static final String ADVICE = IsoAnimalPatch.class.getName() + "$SetHealthAdvice";

    public IsoAnimalPatch() {
        super("zombie.characters.animals.IsoAnimal");
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        return builder.visit(
                Advice.to(typePool.describe(ADVICE).resolve(), locator)
                        .on(
                                ElementMatchers.named("setHealth")
                                        .and(ElementMatchers.takesArguments(float.class))));
    }

    public static class SetHealthAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static float onEnter(@Advice.This Object self) {
            return AnimalFatalHealthDrop.healthBefore(self);
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.This Object self, @Advice.Enter float before) {
            AnimalFatalHealthDrop.afterSet(self, before);
        }
    }
}
