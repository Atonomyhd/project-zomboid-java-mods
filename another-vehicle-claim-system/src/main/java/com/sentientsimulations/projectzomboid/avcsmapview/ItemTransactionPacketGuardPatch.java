package com.sentientsimulations.projectzomboid.avcsmapview;

import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

/**
 * Runs {@link VehicleContainerSecurity#rejectTransactionIfBlocked} ahead of {@code
 * zombie.network.packets.ItemTransactionPacket.processServer}. A blocked request is flagged
 * inconsistent, so the untouched vanilla method answers with its normal Reject and the client's
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

        @Advice.OnMethodEnter
        public static void before(@Advice.This Object packet) {
            VehicleContainerSecurity.rejectTransactionIfBlocked(packet);
        }
    }
}
