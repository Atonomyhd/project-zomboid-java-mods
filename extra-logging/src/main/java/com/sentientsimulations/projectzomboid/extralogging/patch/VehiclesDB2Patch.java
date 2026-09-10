package com.sentientsimulations.projectzomboid.extralogging.patch;

import com.sentientsimulations.projectzomboid.extralogging.VehicleRemovalHandler;
import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

/**
 * Logs the actual {@code DELETE FROM vehicles} in {@code VehiclesDB2.SQLStore.removeVehicle(int)}.
 * This is the last hop for every removal path, including the load-failure delete that never calls
 * {@code permanentlyRemove}.
 */
public class VehiclesDB2Patch extends StormClassTransformer {

    public VehiclesDB2Patch() {
        super("zombie.vehicles.VehiclesDB2$SQLStore");
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        return builder.visit(
                Advice.to(RemoveVehicleAdvice.class)
                        .on(
                                ElementMatchers.named("removeVehicle")
                                        .and(ElementMatchers.takesArguments(int.class))));
    }

    public static class RemoveVehicleAdvice {

        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void onExit(@Advice.Argument(0) int sqlId) {
            VehicleRemovalHandler.onDatabaseDelete(sqlId);
        }
    }
}
