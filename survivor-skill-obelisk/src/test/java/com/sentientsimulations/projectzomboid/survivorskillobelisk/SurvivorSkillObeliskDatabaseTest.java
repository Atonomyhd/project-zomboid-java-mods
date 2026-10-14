package com.sentientsimulations.projectzomboid.survivorskillobelisk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for the Survivor Skill Obelisk schema bootstrap and insert path. Uses a real
 * SQLite DB in a temp dir, matching the survivor-leaderboard test convention.
 */
class SurvivorSkillObeliskDatabaseTest {

    @TempDir File tempDir;

    private SurvivorSkillObeliskDatabase db;
    private SurvivorSkillObeliskRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        db =
                new SurvivorSkillObeliskDatabase(
                        new File(tempDir, "survivor_skill_obelisk.db").getAbsolutePath());
        repo = new SurvivorSkillObeliskRepository(db.getConnection());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void schemaBootstrapsExpectedTables() throws Exception {
        Set<String> tables = new HashSet<>();
        try (Statement stmt = db.getConnection().createStatement();
                ResultSet rs =
                        stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
            while (rs.next()) {
                tables.add(rs.getString("name"));
            }
        }
        assertTrue(tables.contains("deaths"), "deaths table should exist");
        assertTrue(tables.contains("death_skills"), "death_skills table should exist");
        assertTrue(tables.contains("death_recipes"), "death_recipes table should exist");
        assertTrue(
                tables.contains("death_read_literature"),
                "death_read_literature table should exist");
        assertTrue(
                tables.contains("death_read_print_media"),
                "death_read_print_media table should exist");
        assertTrue(
                tables.contains("death_watched_media"), "death_watched_media table should exist");
        assertTrue(
                tables.contains("death_learned_songs"), "death_learned_songs table should exist");
        assertTrue(tables.contains("death_ambitions"), "death_ambitions table should exist");
        assertTrue(
                tables.contains("death_hidden_skills"), "death_hidden_skills table should exist");
        assertTrue(tables.contains("death_inventions"), "death_inventions table should exist");
        assertTrue(tables.contains("recoveries"), "recoveries table should exist");
        assertTrue(tables.contains("recovery_skills"), "recovery_skills table should exist");
        assertTrue(
                tables.contains("character_baselines"), "character_baselines table should exist");
        assertTrue(tables.contains("obelisk_types"), "obelisk_types table should exist");
        assertTrue(tables.contains("blessings"), "blessings table should exist");
    }

    @Test
    void blessingMissingWhenNeverGranted() throws Exception {
        assertNull(repo.findBlessing("main", 42L, "alice"));
        assertTrue(repo.listActiveBlessings("main", 0.0).isEmpty());
    }

    @Test
    void blessingUpsertRoundTripsAndRefreshesExpiry() throws Exception {
        repo.upsertBlessing(
                new SurvivorSkillObeliskRepository.BlessingRow(
                        "main", 42L, "alice", 10, 20, 0, 50, 1000L, 100.0, 124.0));
        SurvivorSkillObeliskRepository.BlessingRow row = repo.findBlessing("main", 42L, "alice");
        assertEquals(10, row.obeliskX());
        assertEquals(20, row.obeliskY());
        assertEquals(0, row.obeliskZ());
        assertEquals(50, row.bonusPercent());
        assertEquals(100.0, row.grantedWorldHours(), 1e-9);
        assertEquals(124.0, row.expiresWorldHours(), 1e-9);

        repo.upsertBlessing(
                new SurvivorSkillObeliskRepository.BlessingRow(
                        "main", 42L, "alice", 11, 21, 1, 75, 2000L, 110.0, 134.0));
        row = repo.findBlessing("main", 42L, "alice");
        assertEquals(11, row.obeliskX());
        assertEquals(75, row.bonusPercent());
        assertEquals(134.0, row.expiresWorldHours(), 1e-9);
        assertEquals(
                1, repo.listActiveBlessings("main", 0.0).size(), "re-granting keeps a single row");
    }

    @Test
    void blessingRowsAreKeyedPerCharacterOfTheSameAccount() throws Exception {
        repo.upsertBlessing(
                new SurvivorSkillObeliskRepository.BlessingRow(
                        "main", 42L, "alice", 0, 0, 0, 50, 1L, 0.0, 24.0));
        repo.upsertBlessing(
                new SurvivorSkillObeliskRepository.BlessingRow(
                        "main", 42L, "alice2", 0, 0, 0, 50, 1L, 0.0, 24.0));
        assertEquals(2, repo.listActiveBlessings("main", 0.0).size());
        assertEquals(24.0, repo.findBlessing("main", 42L, "alice2").expiresWorldHours(), 1e-9);
    }

    @Test
    void listActiveBlessingsExcludesExpiredAndDeleteExpiredDropsThem() throws Exception {
        repo.upsertBlessing(
                new SurvivorSkillObeliskRepository.BlessingRow(
                        "main", 1L, "fresh", 0, 0, 0, 50, 1L, 0.0, 30.0));
        repo.upsertBlessing(
                new SurvivorSkillObeliskRepository.BlessingRow(
                        "main", 2L, "stale", 0, 0, 0, 50, 1L, 0.0, 10.0));
        repo.upsertBlessing(
                new SurvivorSkillObeliskRepository.BlessingRow(
                        "main", 3L, "edge", 0, 0, 0, 50, 1L, 0.0, 20.0));

        List<SurvivorSkillObeliskRepository.BlessingRow> active =
                repo.listActiveBlessings("main", 20.0);
        assertEquals(1, active.size(), "expiry exactly at now counts as expired");
        assertEquals("fresh", active.get(0).username());

        assertEquals(2, repo.deleteExpiredBlessings("main", 20.0));
        assertNull(repo.findBlessing("main", 2L, "stale"));
        assertNull(repo.findBlessing("main", 3L, "edge"));
        assertEquals(30.0, repo.findBlessing("main", 1L, "fresh").expiresWorldHours(), 1e-9);
    }

    @Test
    void blessingRowsAreScopedPerWorld() throws Exception {
        repo.upsertBlessing(
                new SurvivorSkillObeliskRepository.BlessingRow(
                        "main", 42L, "alice", 0, 0, 0, 50, 1L, 500.0, 524.0));
        repo.upsertBlessing(
                new SurvivorSkillObeliskRepository.BlessingRow(
                        "deathmatch", 42L, "alice", 0, 0, 0, 50, 1L, 2.0, 26.0));

        assertEquals(524.0, repo.findBlessing("main", 42L, "alice").expiresWorldHours(), 1e-9);
        assertEquals(26.0, repo.findBlessing("deathmatch", 42L, "alice").expiresWorldHours(), 1e-9);
        assertEquals(1, repo.listActiveBlessings("main", 500.0).size());
        assertEquals(1, repo.listActiveBlessings("deathmatch", 2.0).size());
        assertNull(repo.findBlessing("other", 42L, "alice"));

        assertEquals(0, repo.deleteExpiredBlessings("main", 30.0), "main's row is still ahead");
        assertEquals(1, repo.deleteExpiredBlessings("deathmatch", 30.0));
        assertNull(repo.findBlessing("deathmatch", 42L, "alice"));
        assertEquals(524.0, repo.findBlessing("main", 42L, "alice").expiresWorldHours(), 1e-9);

        repo.deleteBlessing("deathmatch", 42L, "alice");
        assertEquals(524.0, repo.findBlessing("main", 42L, "alice").expiresWorldHours(), 1e-9);
    }

    @Test
    void aBlessingsTableWithoutAWorldColumnIsRebuilt() throws Exception {
        db.close();
        String path = new File(tempDir, "old.db").getAbsolutePath();
        try (java.sql.Connection old = java.sql.DriverManager.getConnection("jdbc:sqlite:" + path);
                Statement stmt = old.createStatement()) {
            stmt.execute(
                    "CREATE TABLE blessings (steam_id INTEGER NOT NULL, username TEXT NOT NULL,"
                            + " obelisk_x INTEGER NOT NULL, obelisk_y INTEGER NOT NULL,"
                            + " obelisk_z INTEGER NOT NULL, bonus_percent INTEGER NOT NULL,"
                            + " granted_ts INTEGER NOT NULL, granted_world_hours REAL NOT NULL,"
                            + " expires_world_hours REAL NOT NULL,"
                            + " PRIMARY KEY (steam_id, username))");
            stmt.execute("INSERT INTO blessings VALUES (1, 'a', 0, 0, 0, 50, 1, 0.0, 24.0)");
        }

        db = new SurvivorSkillObeliskDatabase(path);
        repo = new SurvivorSkillObeliskRepository(db.getConnection());

        assertTrue(repo.listActiveBlessings("", 0.0).isEmpty(), "pre-release rows are dropped");
        repo.upsertBlessing(
                new SurvivorSkillObeliskRepository.BlessingRow(
                        "main", 1L, "a", 0, 0, 0, 50, 1L, 0.0, 24.0));
        assertEquals(24.0, repo.findBlessing("main", 1L, "a").expiresWorldHours(), 1e-9);
    }

    @Test
    void deleteBlessingIsIdempotent() throws Exception {
        repo.deleteBlessing("main", 7L, "nobody");
        repo.upsertBlessing(
                new SurvivorSkillObeliskRepository.BlessingRow(
                        "main", 7L, "bob", 0, 0, 0, 50, 1L, 0.0, 24.0));
        repo.deleteBlessing("main", 7L, "bob");
        assertNull(repo.findBlessing("main", 7L, "bob"));
    }

    @Test
    void recoveryGrantsEmptyWhenPlayerNeverRecovered() throws Exception {
        assertTrue(repo.findRecoveryGrants(42L, "alice").isEmpty());
    }

    @Test
    void replaceRecoveryRoundTripsGrants() throws Exception {
        Map<String, Float> grants = new HashMap<>();
        grants.put("Cooking", 1234.5f);
        grants.put("Strength", 500f);
        repo.replaceRecovery(42L, "alice", 7L, 1_000L, grants);

        Map<String, Float> readBack = repo.findRecoveryGrants(42L, "alice");
        assertEquals(2, readBack.size());
        assertEquals(1234.5f, readBack.get("Cooking"));
        assertEquals(500f, readBack.get("Strength"));

        try (Statement stmt = db.getConnection().createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT death_id, ts FROM recoveries"
                                        + " WHERE steam_id = 42 AND username = 'alice'")) {
            assertTrue(rs.next());
            assertEquals(7L, rs.getLong("death_id"));
            assertEquals(1_000L, rs.getLong("ts"));
        }
    }

    @Test
    void replaceRecoveryOverwritesPreviousLedger() throws Exception {
        // Recovering a second death replaces the ledger wholesale — perks the new recovery didn't
        // grant must vanish, not linger and get subtracted again on the third recovery.
        repo.replaceRecovery(42L, "alice", 7L, 1_000L, Map.of("Cooking", 3000f));
        repo.replaceRecovery(42L, "alice", 9L, 2_000L, Map.of("Strength", 800f));

        Map<String, Float> readBack = repo.findRecoveryGrants(42L, "alice");
        assertEquals(1, readBack.size());
        assertEquals(800f, readBack.get("Strength"));
    }

    @Test
    void deleteRecoveryClearsLedgerForOnePlayerOnly() throws Exception {
        repo.replaceRecovery(42L, "alice", 7L, 1_000L, Map.of("Cooking", 3000f));
        repo.replaceRecovery(43L, "bob", 8L, 1_500L, Map.of("Aiming", 100f));

        repo.deleteRecovery(42L, "alice");

        assertTrue(repo.findRecoveryGrants(42L, "alice").isEmpty());
        assertEquals(100f, repo.findRecoveryGrants(43L, "bob").get("Aiming"));
    }

    @Test
    void deleteRecoveryIsNoOpWhenAbsent() throws Exception {
        // Every death deletes the ledger, including deaths of players who never recovered.
        repo.deleteRecovery(42L, "alice");
        assertTrue(repo.findRecoveryGrants(42L, "alice").isEmpty());
    }

    @Test
    void findCharacterBaselineNullWhenNeverRecorded() throws Exception {
        // Null (not empty map) is the "character predates baseline tracking" signal that makes
        // DeathEventHandler fall back to the trait-replay estimate.
        assertEquals(null, repo.findCharacterBaseline(42L, "alice"));
    }

    @Test
    void replaceCharacterBaselineRoundTrips() throws Exception {
        Map<String, Float> baseline = new HashMap<>();
        baseline.put("Strength", 11250f);
        baseline.put("Fitness", 18750f);
        baseline.put("Running", 0f);
        repo.replaceCharacterBaseline(42L, "alice", baseline);

        Map<String, Float> readBack = repo.findCharacterBaseline(42L, "alice");
        assertEquals(3, readBack.size());
        assertEquals(11250f, readBack.get("Strength"));
        assertEquals(18750f, readBack.get("Fitness"));
        assertEquals(0f, readBack.get("Running"), "xp=0 rows must round-trip verbatim");
    }

    @Test
    void replaceCharacterBaselineOverwritesPreviousCharacter() throws Exception {
        // A respawn is a new character — the old baseline must vanish wholesale, including perks
        // the new character has no grant in.
        repo.replaceCharacterBaseline(42L, "alice", Map.of("Strength", 11250f, "Cooking", 500f));
        repo.replaceCharacterBaseline(42L, "alice", Map.of("Fitness", 18750f));

        Map<String, Float> readBack = repo.findCharacterBaseline(42L, "alice");
        assertEquals(1, readBack.size());
        assertEquals(18750f, readBack.get("Fitness"));
    }

    @Test
    void characterBaselinesAreScopedPerPlayer() throws Exception {
        repo.replaceCharacterBaseline(42L, "alice", Map.of("Strength", 11250f));
        repo.replaceCharacterBaseline(43L, "bob", Map.of("Strength", 3000f));

        repo.replaceCharacterBaseline(42L, "alice", Map.of("Strength", 0f));

        assertEquals(0f, repo.findCharacterBaseline(42L, "alice").get("Strength"));
        assertEquals(3000f, repo.findCharacterBaseline(43L, "bob").get("Strength"));
    }

    @Test
    void newCharacterClearsRecoveryLedger() throws Exception {
        // A death that never reached OnPlayerDeath leaves the dead character's ledger behind.
        repo.replaceRecovery(42L, "alice", 7L, 1_000L, Map.of("Cooking", 3000f));
        repo.replaceRecovery(43L, "bob", 8L, 1_500L, Map.of("Aiming", 100f));

        CharacterBaselineHandler.recordNewCharacter(
                repo, 42L, "alice", Map.of("Strength", 11250f), true);

        assertTrue(repo.findRecoveryGrants(42L, "alice").isEmpty());
        assertEquals(100f, repo.findRecoveryGrants(43L, "bob").get("Aiming"));
        assertEquals(11250f, repo.findCharacterBaseline(42L, "alice").get("Strength"));
    }

    @Test
    void newCharacterKeepsRecoveryLedgerWhereDeathsAreNotRecorded() throws Exception {
        // A server that shares another server's database must not clear that server's ledger.
        repo.replaceRecovery(42L, "alice", 7L, 1_000L, Map.of("Cooking", 3000f));

        CharacterBaselineHandler.recordNewCharacter(
                repo, 42L, "alice", Map.of("Strength", 11250f), false);

        assertEquals(3000f, repo.findRecoveryGrants(42L, "alice").get("Cooking"));
        assertEquals(11250f, repo.findCharacterBaseline(42L, "alice").get("Strength"));
    }

    @Test
    void insertDeathWithSkillsPersists() throws Exception {
        long deathId =
                repo.insertDeath(
                        1_000L, "alice", 42L, "Alice", "Smith", 12.5, 7, 100.0f, 200.0f, 0.0f);
        repo.insertSkill(deathId, "Woodwork", 3, 1234.5f);
        repo.insertSkill(deathId, "Aiming", 1, 50.0f);

        try (Statement stmt = db.getConnection().createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT username, zombie_kills, hours_survived FROM deaths"
                                        + " WHERE id = "
                                        + deathId)) {
            assertTrue(rs.next());
            assertEquals("alice", rs.getString("username"));
            assertEquals(7, rs.getInt("zombie_kills"));
            assertEquals(12.5, rs.getDouble("hours_survived"), 0.0001);
        }

        try (Statement stmt = db.getConnection().createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT COUNT(*) AS c FROM death_skills WHERE death_id = "
                                        + deathId)) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt("c"));
        }
    }

    @Test
    void insertSkillsWithZeroXpRoundTripThroughRepo() throws Exception {
        // DeathEventHandler now saves every perk in PerkFactory.PerkList, including ones the dead
        // character never earned anything in (level=baseline grant, xp=0). The recovery loop
        // iterates these rows to reset the live character's matching perks back to baseline. The
        // DB layer has to carry the xp=0 rows back out of listSkillsByDeath verbatim or the reset
        // never happens.
        long deathId = repo.insertDeath(4_000L, "dan", 11L, "Dan", "Park", 1.0, 0, 0f, 0f, 0f);
        repo.insertSkill(deathId, "Strength", 5, 0f);
        repo.insertSkill(deathId, "Fitness", 5, 0f);
        repo.insertSkill(deathId, "Running", 0, 0f);
        repo.insertSkill(deathId, "Cooking", 7, 12345.5f);

        List<SurvivorSkillObeliskRepository.SkillRow> rows = repo.listSkillsByDeath(deathId);
        assertEquals(4, rows.size(), "every inserted row should come back, including xp=0 ones");

        Map<String, SurvivorSkillObeliskRepository.SkillRow> byPerk = new HashMap<>();
        for (SurvivorSkillObeliskRepository.SkillRow row : rows) {
            byPerk.put(row.perk(), row);
        }
        assertEquals(0f, byPerk.get("Strength").xp());
        assertEquals(5, byPerk.get("Strength").level());
        assertEquals(0f, byPerk.get("Running").xp());
        assertEquals(0, byPerk.get("Running").level());
        assertEquals(12345.5f, byPerk.get("Cooking").xp());
        assertEquals(7, byPerk.get("Cooking").level());
    }

    @Test
    void insertProgressionChildRowsPersist() throws Exception {
        long deathId =
                repo.insertDeath(2_000L, "bob", 7L, "Bob", "Jones", 3.0, 1, 0.0f, 0.0f, 0.0f);

        repo.insertRecipe(deathId, "Make Stir Fry");
        repo.insertReadLiterature(deathId, "BookCarpentry1_translation_42");
        repo.insertReadPrintMedia(deathId, "Base.Newspaper_Dispatch_Day1");
        repo.insertWatchedMedia(
                deathId, "TapeHTV1", 5, "Home-VHS", 1, "Exercise Tape", 2, 3, false);

        assertEquals(1, countChildren("death_recipes", deathId));
        assertEquals(1, countChildren("death_read_literature", deathId));
        assertEquals(1, countChildren("death_read_print_media", deathId));
        assertEquals(1, countChildren("death_watched_media", deathId));

        try (Statement stmt = db.getConnection().createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT literature_title FROM death_read_literature"
                                        + " WHERE death_id = "
                                        + deathId)) {
            assertTrue(rs.next());
            assertEquals("BookCarpentry1_translation_42", rs.getString("literature_title"));
        }

        try (Statement stmt = db.getConnection().createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT lines_watched, line_count, fully_watched"
                                        + " FROM death_watched_media WHERE death_id = "
                                        + deathId)) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt("lines_watched"));
            assertEquals(3, rs.getInt("line_count"));
            assertEquals(0, rs.getInt("fully_watched"));
        }
    }

    @Test
    void insertLifestylesProgressionPersists() throws Exception {
        long deathId =
                repo.insertDeath(3_000L, "carol", 9L, "Carol", "Lee", 8.0, 4, 0.0f, 0.0f, 0.0f);

        repo.insertLearnedSong(
                deathId, "Piano", "ContextMenu_00_01_B", "Piano00LastPost", 3.0, 95.0, 1.0);
        repo.insertLearnedSong(
                deathId, "Banjo", "ContextMenu_02_03_B", "Banjo02HappyBirthday", null, null, null);

        repo.insertAmbition(
                deathId,
                "LSTerminator",
                "Combat",
                "LSTerminator",
                false,
                true,
                false,
                new String[] {"5000", "pain", "0", null, null, null},
                new String[] {"137", "false", "0", null, null, null});
        repo.insertAmbition(
                deathId,
                "LSBladeMaster",
                "Combat",
                null,
                true,
                false,
                false,
                new String[] {"100", null, null, null, null, null},
                new String[] {"100", null, null, null, null, null});

        assertEquals(2, countChildren("death_learned_songs", deathId));
        assertEquals(2, countChildren("death_ambitions", deathId));

        List<SurvivorSkillObeliskRepository.LearnedSongRow> songs =
                repo.listLearnedSongsByDeath(deathId);
        assertEquals(2, songs.size());
        assertEquals("Piano", songs.get(0).instrument());
        assertEquals("ContextMenu_00_01_B", songs.get(0).songName());
        assertEquals("Piano00LastPost", songs.get(0).sound());
        assertEquals(3.0, songs.get(0).level());
        assertEquals(95.0, songs.get(0).length());
        assertEquals(1.0, songs.get(0).isaddon());
        assertEquals("Banjo", songs.get(1).instrument());
        assertEquals(null, songs.get(1).level());
        assertEquals(null, songs.get(1).length());
        assertEquals(null, songs.get(1).isaddon());

        try (Statement stmt = db.getConnection().createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT name, category, texture, completed, is_active, goal1,"
                                        + " goal2, goal1_progress FROM death_ambitions"
                                        + " WHERE death_id = "
                                        + deathId
                                        + " ORDER BY id")) {
            assertTrue(rs.next());
            assertEquals("LSTerminator", rs.getString("name"));
            assertEquals("Combat", rs.getString("category"));
            assertEquals("LSTerminator", rs.getString("texture"));
            assertEquals(0, rs.getInt("completed"));
            assertEquals(1, rs.getInt("is_active"));
            assertEquals("5000", rs.getString("goal1"));
            assertEquals("pain", rs.getString("goal2"));
            assertEquals("137", rs.getString("goal1_progress"));
            assertTrue(rs.next());
            assertEquals("LSBladeMaster", rs.getString("name"));
            assertEquals(null, rs.getString("texture"));
            assertEquals(1, rs.getInt("completed"));
        }
    }

    @Test
    void insertHiddenSkillsRoundTripThroughRepo() throws Exception {
        long deathId =
                repo.insertDeath(5_000L, "erin", 13L, "Erin", "Cho", 20.0, 15, 0.0f, 0.0f, 0.0f);

        repo.insertHiddenSkill(deathId, "Yoga", 4, 320.0, 1000.0);
        repo.insertHiddenSkill(deathId, "Inventing", 0, 0.0, 100.0);

        assertEquals(2, countChildren("death_hidden_skills", deathId));

        Map<String, SurvivorSkillObeliskRepository.HiddenSkillRow> bySkill = new HashMap<>();
        for (SurvivorSkillObeliskRepository.HiddenSkillRow row :
                repo.listHiddenSkillsByDeath(deathId)) {
            bySkill.put(row.skill(), row);
        }
        assertEquals(2, bySkill.size());
        assertEquals(4, bySkill.get("Yoga").level());
        assertEquals(320.0, bySkill.get("Yoga").xp());
        assertEquals(1000.0, bySkill.get("Yoga").xpForNextLevel());
        assertEquals(0, bySkill.get("Inventing").level());
        assertEquals(0.0, bySkill.get("Inventing").xp());
        assertEquals(100.0, bySkill.get("Inventing").xpForNextLevel());
    }

    @Test
    void migrationAddsSongColumnsToLegacyTable() throws Exception {
        // Simulate a DB created by a release where death_learned_songs had no numeric columns:
        // recreate the table in the old shape with a row, then re-open through the schema
        // bootstrap and check the ALTERs land and the legacy row reads back with null fields.
        String path = new File(tempDir, "legacy.db").getAbsolutePath();
        try (java.sql.Connection conn =
                        java.sql.DriverManager.getConnection("jdbc:sqlite:" + path);
                Statement stmt = conn.createStatement()) {
            stmt.execute(
                    """
                    CREATE TABLE death_learned_songs (
                        id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        death_id   INTEGER NOT NULL,
                        instrument TEXT NOT NULL,
                        song_name  TEXT NOT NULL,
                        sound      TEXT
                    )""");
            stmt.execute(
                    "INSERT INTO death_learned_songs (death_id, instrument, song_name, sound)"
                            + " VALUES (1, 'Piano', 'ContextMenu_00_01_B', 'Piano00LastPost')");
        }

        try (SurvivorSkillObeliskDatabase legacyDb = new SurvivorSkillObeliskDatabase(path)) {
            SurvivorSkillObeliskRepository legacyRepo =
                    new SurvivorSkillObeliskRepository(legacyDb.getConnection());
            List<SurvivorSkillObeliskRepository.LearnedSongRow> songs =
                    legacyRepo.listLearnedSongsByDeath(1L);
            assertEquals(1, songs.size());
            assertEquals("Piano", songs.get(0).instrument());
            assertEquals(null, songs.get(0).level());
            assertEquals(null, songs.get(0).length());
            assertEquals(null, songs.get(0).isaddon());

            legacyRepo.insertLearnedSong(
                    1L, "Banjo", "ContextMenu_02_03_B", "Banjo02HappyBirthday", 2.0, 60.0, 2.0);
            songs = legacyRepo.listLearnedSongsByDeath(1L);
            assertEquals(2, songs.size());
            assertEquals(2.0, songs.get(1).level());
        }
    }

    @Test
    void markObeliskNoneInsertsRowWithNoneType() throws Exception {
        repo.markObeliskNone(100, 200, 0, 5_000L);

        assertEquals("None", repo.findObeliskType(100, 200, 0));
        assertEquals(1, countObeliskRows());

        try (Statement stmt = db.getConnection().createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT set_by_username, set_by_steam_id, set_ts"
                                        + " FROM obelisk_types WHERE x = 100 AND y = 200"
                                        + " AND z = 0")) {
            assertTrue(rs.next());
            assertEquals(null, rs.getString("set_by_username"));
            assertEquals(0L, rs.getLong("set_by_steam_id"));
            assertEquals(5_000L, rs.getLong("set_ts"));
        }
    }

    @Test
    void markObeliskNoneOverridesPriorAdminConfiguredType() throws Exception {
        // Admin configures the obelisk.
        repo.upsertObeliskType(100, 200, 0, "Strength", "admin", 42L, 1_000L);
        assertEquals("Strength", repo.findObeliskType(100, 200, 0));

        // Player picks it up + drops it back at the same spot — the placement event
        // resets the type to None and clears setter attribution.
        repo.markObeliskNone(100, 200, 0, 9_999L);

        assertEquals("None", repo.findObeliskType(100, 200, 0));
        try (Statement stmt = db.getConnection().createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT set_by_username, set_by_steam_id, set_ts"
                                        + " FROM obelisk_types WHERE x = 100 AND y = 200"
                                        + " AND z = 0")) {
            assertTrue(rs.next());
            assertEquals(null, rs.getString("set_by_username"));
            assertEquals(0L, rs.getLong("set_by_steam_id"));
            assertEquals(9_999L, rs.getLong("set_ts"));
        }
    }

    @Test
    void deleteObeliskTypeRemovesRow() throws Exception {
        repo.upsertObeliskType(50, 60, 1, "Aiming", "admin", 42L, 1_000L);
        assertEquals(1, countObeliskRows());

        repo.deleteObeliskType(50, 60, 1);

        assertEquals(0, countObeliskRows());
        assertEquals(null, repo.findObeliskType(50, 60, 1));
    }

    @Test
    void deleteObeliskTypeIsNoOpWhenAbsent() throws Exception {
        // Removal-event handlers may fire redundantly (e.g. OnDestroyIsoThumpable +
        // OnObjectAboutToBeRemoved + OnTileRemoved for one sledgehammer hit). DELETE
        // against a missing row must not throw.
        repo.deleteObeliskType(0, 0, 0);
        assertEquals(0, countObeliskRows());
    }

    private int countObeliskRows() throws Exception {
        try (Statement stmt = db.getConnection().createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS c FROM obelisk_types")) {
            assertTrue(rs.next());
            return rs.getInt("c");
        }
    }

    @Test
    void insertInventionsRoundTripThroughRepo() throws Exception {
        long deathId =
                repo.insertDeath(6_000L, "finn", 14L, "Finn", "Oduya", 30.0, 40, 0.0f, 0.0f, 0.0f);
        String json =
                "{\"NeuralHat\":{\"improvementData\":{\"efficiency\":[2,9]}},\"specialMax\":2}";

        repo.insertInventions(deathId, json);

        assertEquals(1, countChildren("death_inventions", deathId));
        assertEquals(json, repo.findInventionsByDeath(deathId));
    }

    @Test
    void findInventionsByDeathIsNullWhenNothingRecorded() throws Exception {
        long deathId =
                repo.insertDeath(6_001L, "finn", 14L, "Finn", "Oduya", 31.0, 41, 0.0f, 0.0f, 0.0f);

        assertNull(repo.findInventionsByDeath(deathId));
        assertEquals(0, countChildren("death_inventions", deathId));
    }

    private int countChildren(String table, long deathId) throws Exception {
        try (Statement stmt = db.getConnection().createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT COUNT(*) AS c FROM "
                                        + table
                                        + " WHERE death_id = "
                                        + deathId)) {
            assertTrue(rs.next());
            return rs.getInt("c");
        }
    }
}
