package com.sentientsimulations.projectzomboid.extralogging.patch;

import com.sentientsimulations.projectzomboid.extralogging.VehicleRemovalContext;
import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;
import zombie.characters.IsoPlayer;

/**
 * Attributes the {@code /remove vehicles} radius wipe ({@code VehicleManager.removeVehicles}) to
 * the admin whose player it was run against.
 */
public class VehicleManagerPatch extends StormClassTransformer {

    public VehicleManagerPatch() {
        super("zombie.vehicles.VehicleManager");
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        return builder.visit(
                Advice.to(RemoveVehiclesAdvice.class)
                        .on(
                                ElementMatchers.named("removeVehicles")
                                        .and(ElementMatchers.takesArguments(IsoPlayer.class))));
    }

    public static class RemoveVehiclesAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.Argument(0) IsoPlayer player) {
            VehicleRemovalContext.enterAdminCommand(
                    "/remove vehicles", player != null ? player.getUsername() : "?");
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void onExit() {
            VehicleRemovalContext.exit();
        }
    }
}
