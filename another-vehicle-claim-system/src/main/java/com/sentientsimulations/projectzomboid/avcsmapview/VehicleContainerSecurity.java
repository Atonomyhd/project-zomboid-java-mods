package com.sentientsimulations.projectzomboid.avcsmapview;

import static io.pzstorm.storm.logging.StormLogger.LOGGER;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.core.logger.LoggerManager;
import zombie.core.raknet.UdpConnection;
import zombie.inventory.ItemContainer;
import zombie.network.GameServer;
import zombie.network.fields.ContainerID;
import zombie.network.fields.character.PlayerID;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehiclePart;

/**
 * Server-side AVCS permission gate for taking items out of a claimed vehicle's containers. AVCS's
 * own container enforcement is client Lua only ({@code ISInventoryTransferAction:isValid} plus loot
 * panel filtering), and the server applies {@code RemoveInventoryItemFromContainerPacket} to any
 * container the client names without checking who sent it. Containers with no door — roof racks,
 * side storage, gloveboxes — have no vanilla gate either, so a modified client, or an honest one
 * whose claim DB has not arrived yet, could empty a claimed vehicle.
 *
 * <p>Two packets carry a client's take-out: {@code ItemTransactionPacket} (the request an honest
 * client waits on before moving anything) and {@code RemoveInventoryItemFromContainerPacket} (the
 * removal itself). {@link ItemTransactionPacketGuardPatch} flags a blocked request inconsistent so
 * vanilla sends the normal Reject and the client's action stops cleanly; {@link
 * RemoveInventoryItemFromContainerPacketGuardPatch} drops a blocked removal whole. The offender is
 * told via the {@code AVCS containerBlocked} client command.
 *
 * <p>The ladder mirrors the Lua {@code AVCS.canAccessVehicleContainer}: unclaimed vehicles pass,
 * then {@link AvcsClaimPermissions#isPermitted} (admin, owner, faction/safehouse members), then the
 * {@code AllowOpeningTrunk} public toggle — which covers only trunk containers ({@link
 * #isTrunkContainerPart}), never every container on the vehicle. Any guard failure fails open.
 */
public final class VehicleContainerSecurity {

    private static final long HALO_THROTTLE_NANOS = 2_000_000_000L;
    private static final Map<String, Long> lastNoteByUsername = new ConcurrentHashMap<>();
    private static final String LOG_NAME = "AVCS";
    private static final Set<String> VANILLA_TRUNK_CONTAINERS =
            Set.of("truckbed", "truckbedopen", "trailertrunk");

    private VehicleContainerSecurity() {}

    /**
     * Returns {@code true} when a {@code RemoveInventoryItemFromContainerPacket} must be dropped.
     */
    public static boolean shouldBlockRemove(Object packetObj, Object connectionObj) {
        try {
            IsoPlayer player = GameServer.getAnyPlayerFromConnection((UdpConnection) connectionObj);
            ContainerID containerId = (ContainerID) field(packetObj, "containerId");
            return block(
                    player, containerId.getContainer(), "RemoveInventoryItemFromContainerPacket");
        } catch (Throwable t) {
            LOGGER.error("[AVCS] vehicle container guard failed; allowing removal", t);
            return false;
        }
    }

    /**
     * Marks an {@code ItemTransactionPacket} request inconsistent when any source container is a
     * claimed vehicle container the requesting player may not take from.
     */
    public static void rejectTransactionIfBlocked(Object packetObj) {
        try {
            Object state = transactionField(packetObj, "state");
            if (state == null || !"Request".equals(((Enum<?>) state).name())) {
                return;
            }
            IsoPlayer player = ((PlayerID) transactionField(packetObj, "playerId")).getPlayer();
            List<?> entries = (List<?>) transactionField(packetObj, "entries");
            for (Object entry : entries) {
                ContainerID sourceId = (ContainerID) field(entry, "sourceId");
                if (block(player, sourceId.getContainer(), "ItemTransactionPacket")) {
                    Field consistent = packetObj.getClass().getField("consistent");
                    consistent.setByte(packetObj, (byte) 1);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.error("[AVCS] vehicle container guard failed; allowing transaction", t);
        }
    }

    static boolean canAccessContainer(IsoPlayer player, ItemContainer container) {
        ItemContainer outermost = container.getOutermostContainer();
        BaseVehicle vehicle = outermost.getVehicle();
        if (vehicle == null && outermost.getParent() instanceof BaseVehicle parent) {
            vehicle = parent;
        }
        if (vehicle == null) {
            return true;
        }
        String owner = AvcsClaimPermissions.claimedOwner(vehicle);
        if (owner == null) {
            return true;
        }
        if (AvcsClaimPermissions.isPermitted(player, owner)) {
            return true;
        }
        return AvcsClaimPermissions.publicPermission(vehicle, "AllowOpeningTrunk")
                && isTrunkContainerPart(outermost.getVehiclePart());
    }

    /**
     * Vanilla trunk containers plus whatever {@code AVCS.TrunkParts} names, so modded cargo parts
     * can be opted in per server.
     */
    static boolean isTrunkContainerPart(VehiclePart part) {
        if (part == null || part.getId() == null) {
            return false;
        }
        String id = part.getId().toLowerCase();
        if (VANILLA_TRUNK_CONTAINERS.contains(id)) {
            return true;
        }
        for (String s : AvcsClaimPermissions.stringOption("AVCS.TrunkParts").split(";")) {
            if (s.trim().toLowerCase().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean block(IsoPlayer player, ItemContainer container, String packetName) {
        if (player == null || player.getUsername() == null || container == null) {
            return false;
        }
        if (canAccessContainer(player, container)) {
            return false;
        }
        ItemContainer outermost = container.getOutermostContainer();
        BaseVehicle vehicle = outermost.getVehicle();
        if (vehicle == null) {
            vehicle = (BaseVehicle) outermost.getParent();
        }
        VehiclePart part = outermost.getVehiclePart();
        String line =
                "["
                        + (System.currentTimeMillis() / 1000L)
                        + "] Warning: Blocked "
                        + packetName
                        + " on claimed vehicle container ["
                        + player.getUsername()
                        + "] [owner "
                        + AvcsClaimPermissions.claimedOwner(vehicle)
                        + "] ["
                        + vehicle.getScriptName()
                        + "] [part "
                        + (part == null ? "?" : part.getId())
                        + "] ["
                        + (int) Math.floor(vehicle.getX())
                        + ","
                        + (int) Math.floor(vehicle.getY())
                        + "]";
        LoggerManager.getLogger(LOG_NAME).write(line);
        LOGGER.warn("[AVCS] {}", line);
        notifyBlocked(player, vehicle);
        return true;
    }

    private static Object transactionField(Object packet, String name)
            throws ReflectiveOperationException {
        Field f = Class.forName("zombie.core.Transaction").getDeclaredField(name);
        f.setAccessible(true);
        return f.get(packet);
    }

    private static Object field(Object o, String name) throws ReflectiveOperationException {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private static void notifyBlocked(IsoPlayer player, BaseVehicle vehicle) {
        String username = player.getUsername();
        long now = System.nanoTime();
        Long last = lastNoteByUsername.get(username);
        if (last != null && now - last < HALO_THROTTLE_NANOS) {
            return;
        }
        lastNoteByUsername.put(username, now);
        KahluaTable args = LuaManager.platform.newTable();
        args.rawset("vehicle", (double) vehicle.getId());
        GameServer.sendServerCommand(player, "AVCS", "containerBlocked", args);
    }
}
