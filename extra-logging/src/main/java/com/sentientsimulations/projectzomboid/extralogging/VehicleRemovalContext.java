package com.sentientsimulations.projectzomboid.extralogging;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import zombie.core.raknet.UdpConnection;

/**
 * Main-thread scope describing who triggered the code currently running, so a vehicle removal deep
 * inside Lua can be attributed to the client command or admin command that caused it. Set by the
 * {@code receiveClientCommand} and {@code VehicleManager.removeVehicles} advices, read by {@link
 * VehicleRemovalHandler}.
 */
public final class VehicleRemovalContext {

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private VehicleRemovalContext() {}

    public static void enterClientCommand(UdpConnection connection, ByteBuffer payload) {
        CURRENT.set(new Scope("clientCommand", connection, payload.duplicate(), null));
    }

    public static void enterAdminCommand(String command, String username) {
        CURRENT.set(new Scope("adminCommand", null, null, command + " by " + username));
    }

    public static void exit() {
        CURRENT.remove();
    }

    public static String describe() {
        Scope scope = CURRENT.get();
        return scope == null ? null : scope.describe();
    }

    private static final class Scope {
        final String kind;
        final UdpConnection connection;
        final ByteBuffer payload;
        final String detail;

        Scope(String kind, UdpConnection connection, ByteBuffer payload, String detail) {
            this.kind = kind;
            this.connection = connection;
            this.payload = payload;
            this.detail = detail;
        }

        String describe() {
            if (connection == null) {
                return kind + " " + detail;
            }
            String role = connection.getRole() != null ? connection.getRole().getName() : "unknown";
            return kind
                    + " "
                    + readCommand()
                    + " user="
                    + readUsername()
                    + " steamId="
                    + connection.getSteamId()
                    + " role="
                    + role;
        }

        String readUsername() {
            int index = payload.get(payload.position());
            if (index >= 0
                    && index < connection.usernames.length
                    && connection.usernames[index] != null) {
                return connection.usernames[index];
            }
            for (String name : connection.usernames) {
                if (name != null) {
                    return name;
                }
            }
            return "?";
        }

        String readCommand() {
            try {
                ByteBuffer bb = payload.duplicate();
                bb.get();
                return readUtf(bb) + "." + readUtf(bb);
            } catch (RuntimeException e) {
                return "?.?";
            }
        }

        private static String readUtf(ByteBuffer bb) {
            byte[] bytes = new byte[bb.getShort()];
            bb.get(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
