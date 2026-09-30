package com.sentientsimulations.projectzomboid.extralogging;

import io.pzstorm.storm.event.packet.*;
import zombie.network.fields.hit.Player;
import zombie.network.fields.vehicle.VehicleID;

public class VehicleEventHandler {

    static final org.slf4j.Logger logger = ExtraLoggerFactory.createLogger("vehicles");

    public static void onPlayerHitVehicle(PlayerHitVehiclePacketEvent event) {
        try {
            Player wielder = (Player) event.getField("wielder");
            VehicleID vehicleId = (VehicleID) event.getField("vehicleId");
            logger.info(
                    "{}: steamId={}, user={}, playerPos=({},{},{}), weapon={}, damage={}, vehiclePos=({},{},{}), vehicleId={}, vehicleName={}",
                    event.getName(),
                    event.steamId,
                    event.username,
                    wielder.getX(),
                    wielder.getY(),
                    wielder.getZ(),
                    event.getPacket().getHandWeapon().getName(),
                    event.getField("damage"),
                    vehicleId.getX(),
                    vehicleId.getY(),
                    vehicleId.getZ(),
                    vehicleId.getVehicle().vehicleId,
                    vehicleId.getVehicle().getScriptName());
        } catch (Exception e) {
            logger.error("Failed to log PlayerHitVehicle", e);
        }
    }
}
