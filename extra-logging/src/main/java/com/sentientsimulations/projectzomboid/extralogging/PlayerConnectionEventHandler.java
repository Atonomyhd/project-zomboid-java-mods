package com.sentientsimulations.projectzomboid.extralogging;

import static io.pzstorm.storm.logging.StormLogger.LOGGER;

import io.pzstorm.storm.event.zomboid.OnPlayerEnterWorldEvent;
import io.pzstorm.storm.event.zomboid.OnPlayerLeaveWorldEvent;
import zombie.characters.IsoPlayer;
import zombie.core.raknet.UdpConnection;

/** Logs every character entering or leaving the world. */
public final class PlayerConnectionEventHandler {

    private PlayerConnectionEventHandler() {}

    public static void onPlayerEnterWorld(OnPlayerEnterWorldEvent event) {
        IsoPlayer player = event.getPlayer();
        UdpConnection connection = event.getConnection();
        if (player == null || connection == null) {
            return;
        }

        LOGGER.info(
                "Player connected: username=\"{}\" displayName=\"{}\" ip={} steamId={}"
                        + " ownerId={} idStr=\"{}\" role={} guid={} index={} onlineId={}"
                        + " coords=({}, {}, {})",
                event.getUsername(),
                player.getDisplayName(),
                connection.getIP(),
                connection.getSteamId(),
                connection.getOwnerId(),
                connection.getIDStr(),
                roleName(connection),
                connection.getConnectedGUID(),
                connection.getIndex(),
                player.getOnlineID(),
                player.getX(),
                player.getY(),
                player.getZ());
    }

    public static void onPlayerLeaveWorld(OnPlayerLeaveWorldEvent event) {
        IsoPlayer player = event.getPlayer();
        UdpConnection connection = event.getConnection();
        if (player == null || connection == null) {
            return;
        }

        LOGGER.info(
                "Player disconnected: username=\"{}\" displayName=\"{}\" ip={} steamId={}"
                        + " idStr=\"{}\" role={} guid={} onlineId={} connectionClosed={}"
                        + " coords=({}, {}, {})",
                event.getUsername(),
                player.getDisplayName(),
                connection.getIP(),
                connection.getSteamId(),
                connection.getIDStr(),
                roleName(connection),
                connection.getConnectedGUID(),
                player.getOnlineID(),
                event.isConnectionClosed(),
                player.getX(),
                player.getY(),
                player.getZ());
    }

    private static String roleName(UdpConnection connection) {
        return connection.getRole() != null ? connection.getRole().getName() : "unknown";
    }
}
