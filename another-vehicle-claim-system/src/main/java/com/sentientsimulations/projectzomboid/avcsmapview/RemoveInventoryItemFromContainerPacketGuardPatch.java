package com.sentientsimulations.projectzomboid.avcsmapview;

import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

/**
 * Gates {@code zombie.network.packets.RemoveInventoryItemFromContainerPacket.processServer} through
 * {@link VehicleContainerSecurity#shouldBlockRemove}: a removal from a claimed vehicle container by
 * a player without container permission is dropped before the server removes anything or relays.
 */
public class RemoveInventoryItemFromContainerPacketGuardPatch extends StormClassTransformer {

    public RemoveInventoryItemFromContainerPacketGuardPatch() {
        super("zombie.network.packets.RemoveInventoryItemFromContainerPacket");
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
                @Advice.This Object packet, @Advice.Argument(1) Object connection) {
            return VehicleContainerSecurity.shouldBlockRemove(packet, connection);
        }
    }
}
