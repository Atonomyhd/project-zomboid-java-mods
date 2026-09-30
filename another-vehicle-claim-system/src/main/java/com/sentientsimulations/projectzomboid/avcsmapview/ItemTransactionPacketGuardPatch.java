package com.sentientsimulations.projectzomboid.avcsmapview;

import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;
import zombie.core.raknet.UdpConnection;

/**
 * Runs {@link VehicleContainerSecurity#rejectTransactionIfBlocked} ahead of {@code
 * zombie.network.packets.ItemTransactionPacket.processServer}. A blocked request gets the same
 * Reject vanilla sends for an inconsistent transaction and vanilla is skipped, so the client's
 * transfer action stops instead of moving the item.
 */
public class ItemTransactionPacketGuardPatch extends StormClassTransformer {

    public ItemTransactionPacketGuardPatch() {
        super("zombie.network.packets.ItemTransactionPacket");
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        return builder.visit(
                Advice.to(ProcessServerAdvice.class).on(ElementMatchers.named("processServer")));
    }

    public static class ProcessServerAdvice {

        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean before(
                @Advice.This Object packet, @Advice.Argument(1) UdpConnection connection) {
            return VehicleContainerSecurity.rejectTransactionIfBlocked(packet, connection);
        }
    }
}
