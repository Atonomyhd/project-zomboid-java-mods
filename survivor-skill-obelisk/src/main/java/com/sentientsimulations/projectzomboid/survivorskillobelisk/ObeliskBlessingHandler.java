package com.sentientsimulations.projectzomboid.survivorskillobelisk;

import static io.pzstorm.storm.logging.StormLogger.LOGGER;

import com.sentientsimulations.projectzomboid.survivorskillobelisk.SurvivorSkillObeliskRepository.BlessingRow;
import io.pzstorm.storm.event.core.OnClientCommand;
import io.pzstorm.storm.event.core.SubscribeEvent;
import io.pzstorm.storm.event.lua.OnServerStartedEvent;
import io.pzstorm.storm.event.lua.OnTickEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.jetbrains.annotations.Nullable;
import se.krka.kahlua.vm.KahluaTable;
import zombie.GameTime;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.network.GameServer;

/**
 * Handles the {@code SurvivorSkillObelisk:receiveBlessing} and {@code getBlessing} client commands
 * and owns the in-memory view of who is blessed.
 *
 * <p>A blessing is a per-character row in {@code blessings} whose expiry is an in-game world-age
 * hour ({@link GameTime#getWorldAgeHours()}), so it ticks down only while the server runs and
 * survives restarts. Receiving a blessing again restarts the timer from "now". Death does not end
 * it: the row is keyed by (steamId, username) like recoveries, and a fresh character of the same
 * player simply inherits whatever time remains. Rows are scoped by {@link #world() world}, because
 * servers that share one database (a PvP raid instance and its main server) each count world age
 * from their own start. Another mod moves a blessing between worlds through {@link #grant}.
 *
 * <p>Same two-thread split as {@link ListDeathsHandler}: the main thread validates and enqueues, a
 * daemon worker verifies the obelisk type and writes SQLite, and the next tick updates the cache
 * and ships the {@code blessingState} reply. {@link ObeliskBlessingXpBoost} reads the cache on
 * every XP gain, so lookups must stay allocation-free and lock-free.
 */
public final class ObeliskBlessingHandler {

    private static final String MODULE = "SurvivorSkillObelisk";
    private static final String REPLY_COMMAND = "blessingState";
    private static final int EXPIRY_SCAN_INTERVAL_TICKS = 60;

    /** Cache entry; {@code expiresWorldHours} is compared against the live world age. */
    public record Blessing(long steamId, String username, double expiresWorldHours) {}

    private record PendingRequest(
            IsoPlayer player,
            String username,
            long steamId,
            int x,
            int y,
            int z,
            double nowWorldHours) {}

    private record CompletedRequest(
            IsoPlayer player, @Nullable Blessing blessing, boolean granted, String reason) {}

    private static final BlockingQueue<Runnable> WORK = new LinkedBlockingQueue<>();
    private static final ConcurrentLinkedQueue<CompletedRequest> COMPLETED =
            new ConcurrentLinkedQueue<>();
    private static final Map<String, Blessing> ACTIVE = new ConcurrentHashMap<>();

    private static int ticksSinceExpiryScan = 0;

    static {
        Thread worker =
                new Thread(
                        ObeliskBlessingHandler::workerLoop, "SurvivorSkillObelisk-Blessing-Worker");
        worker.setDaemon(true);
        worker.start();
    }

    private ObeliskBlessingHandler() {}

    /**
     * The player's unexpired blessing, or {@code null}. Expired entries are evicted lazily here and
     * announced to the client by the periodic scan in {@link #onTick}.
     */
    public static @Nullable Blessing activeBlessing(IsoPlayer player) {
        if (ACTIVE.isEmpty()) {
            return null;
        }
        String username = player.getUsername();
        if (username == null) {
            return null;
        }
        Blessing blessing = ACTIVE.get(cacheKey(player.getSteamID(), username));
        if (blessing == null) {
            return null;
        }
        if (blessing.expiresWorldHours() <= nowWorldHours()) {
            return null;
        }
        return blessing;
    }

    /** Hours of blessing left for the player, or 0 when none is active. */
    public static double remainingHours(IsoPlayer player) {
        Blessing blessing = activeBlessing(player);
        return blessing == null ? 0.0 : blessing.expiresWorldHours() - nowWorldHours();
    }

    /**
     * Blesses a character for {@code hours} from now without an obelisk, for another mod that
     * carries a blessing in from elsewhere. A longer blessing already running is kept. The player
     * need not be online; the cache answers at once and the row follows on the worker. Returns
     * false when nothing was granted.
     */
    public static boolean grant(long steamId, String username, double hours, String source) {
        if (username == null || username.isBlank() || !(hours > 0)) {
            return false;
        }
        double now = nowWorldHours();
        String key = cacheKey(steamId, username);
        Blessing existing = ACTIVE.get(key);
        double expires = extendedExpiry(now, hours, existing);
        Blessing blessing = new Blessing(steamId, username, expires);
        ACTIVE.put(key, blessing);
        int bonusPercent = SurvivorSkillObeliskConfig.getBlessingXpBonusPercent();
        BlessingRow row =
                new BlessingRow(
                        world(),
                        steamId,
                        username,
                        0,
                        0,
                        0,
                        bonusPercent,
                        System.currentTimeMillis(),
                        now,
                        expires);
        WORK.offer(() -> writeGranted(row, source));
        LOGGER.info(
                "[SurvivorSkillObelisk] {} ({}) blessed by {} for {}h at +{}% XP, expires at world"
                        + " age {}h",
                username,
                steamId,
                source,
                String.format("%.1f", expires - now),
                bonusPercent,
                String.format("%.1f", expires));
        IsoPlayer player = GameServer.getPlayerByUserName(username);
        if (player != null && player.getSteamID() == steamId) {
            sendState(player, blessing, true, "granted");
        }
        return true;
    }

    /** The later of the running blessing's expiry and {@code now + hours}. */
    static double extendedExpiry(double now, double hours, @Nullable Blessing existing) {
        double expires = now + hours;
        if (existing != null && existing.expiresWorldHours() > expires) {
            return existing.expiresWorldHours();
        }
        return expires;
    }

    /** The save the server runs; the scope of its blessing rows. */
    static String world() {
        String world = Core.gameSaveWorld;
        return world == null ? "" : world;
    }

    @SubscribeEvent
    public static void onServerStarted(OnServerStartedEvent event) {
        WORK.offer(ObeliskBlessingHandler::loadActiveBlessings);
    }

    @OnClientCommand
    public static void onReceiveBlessing(ReceiveBlessingCommand event) {
        IsoPlayer player = event.getPlayer();
        if (player == null) {
            LOGGER.warn("[SurvivorSkillObelisk] receiveBlessing from null player; dropping");
            return;
        }
        String username = player.getUsername();
        long steamId = player.getSteamID();
        Integer x = event.getX();
        Integer y = event.getY();
        Integer z = event.getZ();
        if (username == null || x == null || y == null || z == null) {
            LOGGER.warn(
                    "[SurvivorSkillObelisk] receiveBlessing from {} with missing coords"
                            + " (x={}, y={}, z={}); dropping",
                    username,
                    x,
                    y,
                    z);
            return;
        }
        PendingRequest req =
                new PendingRequest(player, username, steamId, x, y, z, nowWorldHours());
        WORK.offer(() -> runGrant(req));
    }

    @OnClientCommand
    public static void onGetBlessing(GetBlessingCommand event) {
        IsoPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }
        sendState(player, activeBlessing(player), false, "query");
    }

    @SubscribeEvent
    public static void onTick(OnTickEvent event) {
        CompletedRequest done;
        while ((done = COMPLETED.poll()) != null) {
            if (done.blessing() != null) {
                ACTIVE.put(
                        cacheKey(done.blessing().steamId(), done.blessing().username()),
                        done.blessing());
            }
            sendState(done.player(), done.blessing(), done.granted(), done.reason());
        }
        if (++ticksSinceExpiryScan >= EXPIRY_SCAN_INTERVAL_TICKS) {
            ticksSinceExpiryScan = 0;
            evictExpired();
        }
    }

    private static void evictExpired() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        double now = nowWorldHours();
        List<Blessing> expired = new ArrayList<>();
        for (Blessing blessing : ACTIVE.values()) {
            if (blessing.expiresWorldHours() <= now) {
                expired.add(blessing);
            }
        }
        for (Blessing blessing : expired) {
            ACTIVE.remove(cacheKey(blessing.steamId(), blessing.username()));
            LOGGER.info(
                    "[SurvivorSkillObelisk] blessing expired for {} ({})",
                    blessing.username(),
                    blessing.steamId());
            IsoPlayer player = GameServer.getPlayerByUserName(blessing.username());
            if (player != null && player.getSteamID() == blessing.steamId()) {
                sendState(player, null, false, "expired");
            }
        }
    }

    private static void sendState(
            IsoPlayer player, @Nullable Blessing blessing, boolean granted, String reason) {
        try {
            KahluaTable reply = LuaManager.platform.newTable();
            double now = nowWorldHours();
            boolean active = blessing != null && blessing.expiresWorldHours() > now;
            reply.rawset("active", active);
            reply.rawset("granted", granted);
            reply.rawset("reason", reason);
            reply.rawset(
                    "bonusPercent",
                    (double) SurvivorSkillObeliskConfig.getBlessingXpBonusPercent());
            reply.rawset(
                    "durationHours",
                    (double) SurvivorSkillObeliskConfig.getBlessingDurationHours());
            reply.rawset("nowWorldHours", now);
            if (active) {
                reply.rawset("expiresWorldHours", blessing.expiresWorldHours());
                reply.rawset("remainingHours", blessing.expiresWorldHours() - now);
            }
            GameServer.sendServerCommand(player, MODULE, REPLY_COMMAND, reply);
        } catch (Exception e) {
            LOGGER.warn(
                    "[SurvivorSkillObelisk] blessingState send failed for {}: {}",
                    player.getUsername(),
                    e.getMessage());
        }
    }

    private static void workerLoop() {
        while (true) {
            Runnable job;
            try {
                job = WORK.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                job.run();
            } catch (Throwable t) {
                LOGGER.error(
                        "[SurvivorSkillObelisk] blessing worker iteration failed: {}",
                        t.getMessage(),
                        t);
            }
        }
    }

    private static void loadActiveBlessings() {
        double now = nowWorldHours();
        try (SurvivorSkillObeliskDatabase db =
                new SurvivorSkillObeliskDatabase(DeathEventHandler.getDbPath())) {
            SurvivorSkillObeliskRepository repo =
                    new SurvivorSkillObeliskRepository(db.getConnection());
            int dropped = repo.deleteExpiredBlessings(world(), now);
            List<BlessingRow> rows = repo.listActiveBlessings(world(), now);
            for (BlessingRow row : rows) {
                ACTIVE.put(
                        cacheKey(row.steamId(), row.username()),
                        new Blessing(row.steamId(), row.username(), row.expiresWorldHours()));
            }
            LOGGER.info(
                    "[SurvivorSkillObelisk] loaded {} active blessing(s), dropped {} expired"
                            + " (world age {}h)",
                    rows.size(),
                    dropped,
                    String.format("%.1f", now));
        } catch (Exception e) {
            LOGGER.error("[SurvivorSkillObelisk] failed to load blessings", e);
        }
    }

    private static void runGrant(PendingRequest req) {
        try (SurvivorSkillObeliskDatabase db =
                new SurvivorSkillObeliskDatabase(DeathEventHandler.getDbPath())) {
            SurvivorSkillObeliskRepository repo =
                    new SurvivorSkillObeliskRepository(db.getConnection());
            String type = repo.findObeliskType(req.x(), req.y(), req.z());
            if (!ObeliskTypes.BLESSING.equals(type)) {
                LOGGER.warn(
                        "[SurvivorSkillObelisk] receiveBlessing from {} ({}) at ({}, {}, {}) but"
                                + " obelisk type is '{}'; refusing",
                        req.username(),
                        req.steamId(),
                        req.x(),
                        req.y(),
                        req.z(),
                        type);
                COMPLETED.offer(
                        new CompletedRequest(
                                req.player(),
                                ACTIVE.get(cacheKey(req.steamId(), req.username())),
                                false,
                                "notBlessingObelisk"));
                return;
            }
            int bonusPercent = SurvivorSkillObeliskConfig.getBlessingXpBonusPercent();
            int durationHours = SurvivorSkillObeliskConfig.getBlessingDurationHours();
            double expires = req.nowWorldHours() + durationHours;
            repo.upsertBlessing(
                    new BlessingRow(
                            world(),
                            req.steamId(),
                            req.username(),
                            req.x(),
                            req.y(),
                            req.z(),
                            bonusPercent,
                            System.currentTimeMillis(),
                            req.nowWorldHours(),
                            expires));
            LOGGER.info(
                    "[SurvivorSkillObelisk] receiveBlessing: {} ({}) blessed at ({}, {}, {}) for"
                            + " {}h at +{}% XP, expires at world age {}h",
                    req.username(),
                    req.steamId(),
                    req.x(),
                    req.y(),
                    req.z(),
                    durationHours,
                    bonusPercent,
                    String.format("%.1f", expires));
            COMPLETED.offer(
                    new CompletedRequest(
                            req.player(),
                            new Blessing(req.steamId(), req.username(), expires),
                            true,
                            "granted"));
        } catch (Exception e) {
            LOGGER.error(
                    "[SurvivorSkillObelisk] receiveBlessing failed for {} ({}) at ({}, {}, {})",
                    req.username(),
                    req.steamId(),
                    req.x(),
                    req.y(),
                    req.z(),
                    e);
            COMPLETED.offer(
                    new CompletedRequest(
                            req.player(),
                            ACTIVE.get(cacheKey(req.steamId(), req.username())),
                            false,
                            "error"));
        }
    }

    private static void writeGranted(BlessingRow row, String source) {
        try (SurvivorSkillObeliskDatabase db =
                new SurvivorSkillObeliskDatabase(DeathEventHandler.getDbPath())) {
            new SurvivorSkillObeliskRepository(db.getConnection()).upsertBlessing(row);
        } catch (Exception e) {
            LOGGER.error(
                    "[SurvivorSkillObelisk] blessing of {} ({}) from {} not written; it ends at the"
                            + " next restart",
                    row.username(),
                    row.steamId(),
                    source,
                    e);
        }
    }

    private static double nowWorldHours() {
        GameTime time = GameTime.getInstance();
        return time == null ? 0.0 : time.getWorldAgeHours();
    }

    static String cacheKey(long steamId, String username) {
        return steamId + "|" + username;
    }
}
