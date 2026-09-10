package com.sentientsimulations.projectzomboid.extralogging;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import se.krka.kahlua.vm.Coroutine;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.LuaCallFrame;
import zombie.Lua.LuaManager;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.network.GameServer;
import zombie.vehicles.BaseVehicle;

/**
 * Audit log for every path that destroys a vehicle. {@link #onPermanentlyRemove} fires for every
 * intentional removal (client {@code vehicle.remove}, {@code /remove vehicles}, burnt-vehicle
 * salvage, mod Lua); {@link #onDatabaseDelete} fires on the world-streamer thread for the actual
 * {@code DELETE FROM vehicles} row and flags rows that were never announced by {@code
 * permanentlyRemove} (the load-failure delete in {@code VehiclesDB2}).
 */
public final class VehicleRemovalHandler {

    static final org.slf4j.Logger logger = ExtraLoggerFactory.createLogger("vehicle-removals");

    private static final int NEARBY_RADIUS = 20;
    private static final int MAX_JAVA_FRAMES = 6;
    private static final int MAX_LUA_FRAMES = 8;
    private static final Set<Integer> ANNOUNCED = ConcurrentHashMap.newKeySet();

    private VehicleRemovalHandler() {}

    public static void onPermanentlyRemove(Object self) {
        try {
            BaseVehicle vehicle = (BaseVehicle) self;
            if (vehicle.sqlId >= 1) {
                ANNOUNCED.add(vehicle.sqlId);
            }
            String actor = VehicleRemovalContext.describe();
            logger.info(
                    "permanentlyRemove: sqlId={} vehicleId={} script={} pos=({},{},{}) claimKey={}"
                            + " keyId={} occupants={} actor={} nearby={} lua={} java={}",
                    vehicle.sqlId,
                    vehicle.getId(),
                    vehicle.getScriptName(),
                    vehicle.getX(),
                    vehicle.getY(),
                    vehicle.getZ(),
                    claimKey(vehicle),
                    vehicle.getKeyId(),
                    occupants(vehicle),
                    actor != null ? actor : "none",
                    nearbyPlayers(vehicle),
                    luaStack(),
                    javaStack());
        } catch (Exception e) {
            logger.error("Failed to log permanentlyRemove", e);
        }
    }

    public static void onDatabaseDelete(int sqlId) {
        try {
            boolean announced = ANNOUNCED.remove(sqlId);
            if (announced) {
                logger.info("dbDelete: sqlId={} announced=true", sqlId);
            } else {
                logger.warn(
                        "dbDelete: sqlId={} announced=false (no permanentlyRemove; load-failure"
                                + " delete or unknown path) java={}",
                        sqlId,
                        javaStack());
            }
        } catch (Exception e) {
            logger.error("Failed to log dbDelete", e);
        }
    }

    private static Object claimKey(BaseVehicle vehicle) {
        KahluaTable modData = vehicle.getModData();
        if (modData == null) {
            return null;
        }
        Object key = modData.rawget("SQLID");
        return key instanceof Double d && d == Math.rint(d) ? (Object) d.longValue() : key;
    }

    private static List<String> occupants(BaseVehicle vehicle) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < vehicle.getMaxPassengers(); i++) {
            IsoGameCharacter chr = vehicle.getCharacter(i);
            if (chr instanceof IsoPlayer p) {
                names.add(p.getUsername());
            } else if (chr != null) {
                names.add(chr.getClass().getSimpleName());
            }
        }
        return names;
    }

    private static List<String> nearbyPlayers(BaseVehicle vehicle) {
        List<String> names = new ArrayList<>();
        for (IsoPlayer p : GameServer.Players) {
            float dx = p.getX() - vehicle.getX();
            float dy = p.getY() - vehicle.getY();
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist <= NEARBY_RADIUS) {
                names.add(p.getUsername() + "@" + Math.round(dist));
            }
        }
        return names;
    }

    private static List<String> luaStack() {
        List<String> frames = new ArrayList<>();
        if (LuaManager.thread == null) {
            return frames;
        }
        Coroutine coroutine = LuaManager.thread.currentCoroutine;
        if (coroutine == null) {
            return frames;
        }
        for (int i = coroutine.getCallframeTop() - 1;
                i >= 0 && frames.size() < MAX_LUA_FRAMES;
                i--) {
            LuaCallFrame frame = coroutine.getCallFrame(i);
            if (frame == null || frame.closure == null || frame.closure.prototype == null) {
                continue;
            }
            String file = frame.closure.prototype.filename;
            String name = frame.closure.prototype.name;
            int slash = file != null ? file.lastIndexOf('/') : -1;
            frames.add((slash >= 0 ? file.substring(slash + 1) : file) + ":" + name);
        }
        return frames;
    }

    private static List<String> javaStack() {
        List<String> frames = new ArrayList<>();
        String last = null;
        for (StackTraceElement el : Thread.currentThread().getStackTrace()) {
            String cls = el.getClassName();
            if (!cls.startsWith("zombie.")) {
                continue;
            }
            String frame = cls.substring(cls.lastIndexOf('.') + 1) + "." + el.getMethodName();
            if (frame.equals(last)) {
                continue;
            }
            frames.add(frame);
            last = frame;
            if (frames.size() >= MAX_JAVA_FRAMES) {
                break;
            }
        }
        return frames;
    }
}
