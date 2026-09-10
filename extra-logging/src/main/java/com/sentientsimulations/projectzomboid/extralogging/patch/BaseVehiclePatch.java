package com.sentientsimulations.projectzomboid.extralogging.patch;

import com.sentientsimulations.projectzomboid.extralogging.VehicleRemovalHandler;
import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

/** Logs every {@code BaseVehicle.permanentlyRemove()} before the vehicle leaves the world. */
public class BaseVehiclePatch extends StormClassTransformer {

    public BaseVehiclePatch() {
        super("zombie.vehicles.BaseVehicle");
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        return builder.visit(
                Advice.to(PermanentlyRemoveAdvice.class)
                        .on(ElementMatchers.named("permanentlyRemove")));
    }

    public static class PermanentlyRemoveAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This Object vehicle) {
            VehicleRemovalHandler.onPermanentlyRemove(vehicle);
        }
    }
}
