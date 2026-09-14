package com.sentientsimulations.projectzomboid.extralogging.patch;

import com.sentientsimulations.projectzomboid.extralogging.VehicleRemovalContext;
import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;
import zombie.core.network.ByteBufferReader;
import zombie.core.raknet.UdpConnection;

/**
 * Patches {@link zombie.network.GameServer} to scope each client command so vehicle removals inside
 * it can be attributed to the sender. Player connect and disconnect logging comes from Storm's
 * {@code OnPlayerEnterWorldEvent} / {@code OnPlayerLeaveWorldEvent}, see {@link
 * com.sentientsimulations.projectzomboid.extralogging.PlayerConnectionEventHandler}.
 */
public class GameServerPatch extends StormClassTransformer {

    public GameServerPatch() {
        super("zombie.network.GameServer");
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        return builder.visit(
                Advice.to(ReceiveClientCommandAdvice.class)
                        .on(ElementMatchers.named("receiveClientCommand")));
    }

    public static class ReceiveClientCommandAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(
                @Advice.Argument(0) ByteBufferReader bb,
                @Advice.Argument(1) UdpConnection connection) {
            VehicleRemovalContext.enterClientCommand(connection, bb.bb);
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void onExit() {
            VehicleRemovalContext.exit();
        }
    }
}
