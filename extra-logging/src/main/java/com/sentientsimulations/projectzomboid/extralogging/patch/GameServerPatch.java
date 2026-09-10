package com.sentientsimulations.projectzomboid.extralogging.patch;

import static io.pzstorm.storm.logging.StormLogger.LOGGER;

import com.sentientsimulations.projectzomboid.extralogging.VehicleRemovalContext;
import io.pzstorm.storm.core.StormClassTransformer;
import io.pzstorm.storm.event.core.StormEventDispatcher;
import io.pzstorm.storm.event.lua.OnPlayerDisconnectedEvent;
import io.pzstorm.storm.event.lua.OnPlayerFullyConnectedEvent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;
import zombie.characters.IsoPlayer;
import zombie.core.network.ByteBufferReader;
import zombie.core.raknet.UdpConnection;
import zombie.network.IConnection;

/**
 * Patches {@link zombie.network.GameServer} to log and dispatch player connection events, and to
 * scope each client command so vehicle removals inside it can be attributed to the sender.
 */
public class GameServerPatch extends StormClassTransformer {

    public GameServerPatch() {
        super("zombie.network.GameServer");
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        return builder.visit(
                        Advice.to(ReceivePlayerConnectAdvice.class)
                                .on(ElementMatchers.named("receivePlayerConnect")))
                .visit(
                        Advice.to(DisconnectPlayerAdvice.class)
                                .on(ElementMatchers.named("disconnectPlayer")))
                .visit(
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

    public static class ReceivePlayerConnectAdvice {

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void afterReceivePlayerConnect(
                @Advice.Argument(1) IConnection connection, @Advice.Argument(2) String username) {

            UdpConnection udpCon = (UdpConnection) connection;

            IsoPlayer connectedPlayer = null;
            for (int i = 0; i < 4; i++) {
                IsoPlayer p = udpCon.players[i];
                if (p != null && username.equals(p.username)) {
                    connectedPlayer = p;
                    break;
                }
            }

            if (connectedPlayer == null) {
                return;
            }

            String ip = udpCon.getIP();
            long steamId = udpCon.getSteamId();
            long ownerId = udpCon.getOwnerId();
            String idStr = udpCon.getIDStr();
            String roleName = udpCon.getRole() != null ? udpCon.getRole().getName() : "unknown";
            long guid = udpCon.getConnectedGUID();
            int index = udpCon.getIndex();
            short onlineId = connectedPlayer.getOnlineID();
            float x = connectedPlayer.getX();
            float y = connectedPlayer.getY();
            float z = connectedPlayer.getZ();
            String displayName = connectedPlayer.getDisplayName();

            LOGGER.info(
                    "Player connected: username=\"{}\" displayName=\"{}\" ip={} steamId={}"
                            + " ownerId={} idStr=\"{}\" role={} guid={} index={} onlineId={}"
                            + " coords=({}, {}, {})",
                    username,
                    displayName,
                    ip,
                    steamId,
                    ownerId,
                    idStr,
                    roleName,
                    guid,
                    index,
                    onlineId,
                    x,
                    y,
                    z);

            OnPlayerFullyConnectedEvent event =
                    new OnPlayerFullyConnectedEvent(
                            username,
                            displayName,
                            ip,
                            steamId,
                            ownerId,
                            idStr,
                            roleName,
                            guid,
                            index,
                            onlineId,
                            x,
                            y,
                            z);
            StormEventDispatcher.dispatchEvent(event);
        }
    }

    public static class DisconnectPlayerAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void beforeDisconnectPlayer(
                @Advice.Argument(0) IsoPlayer player, @Advice.Argument(1) IConnection connection) {

            if (player == null) {
                return;
            }

            UdpConnection udpCon = (UdpConnection) connection;

            String username = player.getUsername();
            String displayName = player.getDisplayName();
            String ip = udpCon.getIP();
            long steamId = udpCon.getSteamId();
            String idStr = udpCon.getIDStr();
            String roleName = udpCon.getRole() != null ? udpCon.getRole().getName() : "unknown";
            long guid = udpCon.getConnectedGUID();
            short onlineId = player.getOnlineID();
            float x = player.getX();
            float y = player.getY();
            float z = player.getZ();

            LOGGER.info(
                    "Player disconnected: username=\"{}\" displayName=\"{}\" ip={} steamId={}"
                            + " idStr=\"{}\" role={} guid={} onlineId={} coords=({}, {}, {})",
                    username,
                    displayName,
                    ip,
                    steamId,
                    idStr,
                    roleName,
                    guid,
                    onlineId,
                    x,
                    y,
                    z);

            OnPlayerDisconnectedEvent event =
                    new OnPlayerDisconnectedEvent(
                            username,
                            displayName,
                            ip,
                            steamId,
                            idStr,
                            roleName,
                            guid,
                            onlineId,
                            x,
                            y,
                            z);
            StormEventDispatcher.dispatchEvent(event);
        }
    }
}
