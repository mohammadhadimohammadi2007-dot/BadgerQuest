package ir.badgerquest.plugin.db;

import ir.badgerquest.plugin.BadgerQuest;
import ir.badgerquest.plugin.config.ConfigManager;
import ir.badgerquest.plugin.config.PoolEntry;
import ir.badgerquest.plugin.quest.DailyQuests;
import ir.badgerquest.plugin.quest.Quest;
import ir.badgerquest.plugin.quest.QuestObjective;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Single-connection SQLite wrapper with a dedicated single-thread executor.
 * Every write is queued on that executor — the main server thread never blocks on IO.
 */
public final class Database {

    private final BadgerQuest plugin;
    private Connection connection;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BadgerQuest-DB");
        t.setDaemon(true);
        return t;
    });

    public Database(BadgerQuest plugin) {
        this.plugin = plugin;
    }

    public void open() throws SQLException {
        File dir = plugin.getDataFolder();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new SQLException("Could not create plugin data folder");
        }
        File file = new File(dir, plugin.config().databaseFile());
        try {
            Class.forName("ir.badgerquest.libs.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            // fall back to unrelocated driver during dev
            try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException ignored) {}
        }
        String url = "jdbc:sqlite:" + file.getAbsolutePath();
        connection = DriverManager.getConnection(url);
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("PRAGMA foreign_keys=ON");
        }
        migrate();
    }

    public void close() {
        io.shutdown();
        try { io.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) {}
        }
    }

    private void migrate() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid TEXT PRIMARY KEY,
                    streak INTEGER NOT NULL DEFAULT 0,
                    highscore INTEGER NOT NULL DEFAULT 0,
                    last_completion_day INTEGER NOT NULL DEFAULT 0,
                    last_seen_day INTEGER NOT NULL DEFAULT 0
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS player_daily (
                    uuid TEXT NOT NULL,
                    day_key INTEGER NOT NULL,
                    quest_index INTEGER NOT NULL,
                    accepted INTEGER NOT NULL DEFAULT 0,
                    completed INTEGER NOT NULL DEFAULT 0,
                    accepted_at INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (uuid, day_key, quest_index)
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS player_daily_objective (
                    uuid TEXT NOT NULL,
                    day_key INTEGER NOT NULL,
                    quest_index INTEGER NOT NULL,
                    slot INTEGER NOT NULL,
                    pool_id TEXT NOT NULL,
                    required INTEGER NOT NULL,
                    collected INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (uuid, day_key, quest_index, slot)
                )""");
            s.execute("CREATE INDEX IF NOT EXISTS idx_pdo_lookup ON player_daily_objective(uuid, day_key)");
        }
    }

    // --------------------------------------------------------------------
    //  Player record
    // --------------------------------------------------------------------
    public CompletableFuture<PlayerRecord> loadPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT streak, highscore, last_completion_day, last_seen_day FROM players WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new PlayerRecord(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4));
                    }
                }
                return new PlayerRecord();
            } catch (SQLException e) {
                plugin.getLogger().severe("loadPlayer failed: " + e.getMessage());
                return new PlayerRecord();
            }
        }, io);
    }

    public CompletableFuture<Void> savePlayer(UUID uuid, PlayerRecord rec) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO players(uuid, streak, highscore, last_completion_day, last_seen_day)
                    VALUES(?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        streak=excluded.streak,
                        highscore=excluded.highscore,
                        last_completion_day=excluded.last_completion_day,
                        last_seen_day=excluded.last_seen_day
                    """)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, rec.streak);
                ps.setInt(3, rec.highscore);
                ps.setInt(4, rec.lastCompletionDay);
                ps.setInt(5, rec.lastSeenDay);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("savePlayer failed: " + e.getMessage());
            }
        }, io);
    }

    // --------------------------------------------------------------------
    //  DailyQuests persistence
    // --------------------------------------------------------------------
    public CompletableFuture<DailyQuests> loadDaily(UUID uuid, int dayKey) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ConfigManager cfg = plugin.config();
                List<Quest> quests = new ArrayList<>();
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT quest_index, accepted, completed, accepted_at FROM player_daily WHERE uuid=? AND day_key=? ORDER BY quest_index")) {
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, dayKey);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int index = rs.getInt(1);
                            boolean accepted = rs.getInt(2) != 0;
                            boolean completed = rs.getInt(3) != 0;
                            long acceptedAt = rs.getLong(4);
                            List<QuestObjective> objs = loadObjectives(uuid, dayKey, index, cfg);
                            if (objs.isEmpty()) continue;
                            quests.add(new Quest(index, objs, accepted, completed, acceptedAt));
                        }
                    }
                }
                if (quests.isEmpty()) return null;
                return new DailyQuests(uuid, dayKey, quests);
            } catch (SQLException e) {
                plugin.getLogger().severe("loadDaily failed: " + e.getMessage());
                return null;
            }
        }, io);
    }

    private List<QuestObjective> loadObjectives(UUID uuid, int dayKey, int questIndex, ConfigManager cfg) throws SQLException {
        List<QuestObjective> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pool_id, required, collected FROM player_daily_objective WHERE uuid=? AND day_key=? AND quest_index=? ORDER BY slot")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, dayKey);
            ps.setInt(3, questIndex);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String poolId = rs.getString(1);
                    int required = rs.getInt(2);
                    int collected = rs.getInt(3);
                    PoolEntry pe = cfg.poolEntry(poolId);
                    if (pe == null) {
                        // Item was removed from config since save; skip.
                        continue;
                    }
                    list.add(new QuestObjective(pe, required, collected));
                }
            }
        }
        return list;
    }

    public CompletableFuture<Void> saveDaily(DailyQuests daily) {
        return CompletableFuture.runAsync(() -> {
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement del1 = connection.prepareStatement(
                        "DELETE FROM player_daily WHERE uuid=? AND day_key=?");
                     PreparedStatement del2 = connection.prepareStatement(
                             "DELETE FROM player_daily_objective WHERE uuid=? AND day_key=?")) {
                    del1.setString(1, daily.playerId().toString());
                    del1.setInt(2, daily.dayKey());
                    del1.executeUpdate();
                    del2.setString(1, daily.playerId().toString());
                    del2.setInt(2, daily.dayKey());
                    del2.executeUpdate();
                }
                try (PreparedStatement ins1 = connection.prepareStatement(
                        "INSERT INTO player_daily(uuid, day_key, quest_index, accepted, completed, accepted_at) VALUES(?, ?, ?, ?, ?, ?)");
                     PreparedStatement ins2 = connection.prepareStatement(
                             "INSERT INTO player_daily_objective(uuid, day_key, quest_index, slot, pool_id, required, collected) VALUES(?, ?, ?, ?, ?, ?, ?)")) {
                    for (Quest q : daily.quests()) {
                        ins1.setString(1, daily.playerId().toString());
                        ins1.setInt(2, daily.dayKey());
                        ins1.setInt(3, q.index());
                        ins1.setInt(4, q.accepted() ? 1 : 0);
                        ins1.setInt(5, q.completed() ? 1 : 0);
                        ins1.setLong(6, q.acceptedAt());
                        ins1.addBatch();
                        int slot = 0;
                        for (QuestObjective o : q.objectives()) {
                            ins2.setString(1, daily.playerId().toString());
                            ins2.setInt(2, daily.dayKey());
                            ins2.setInt(3, q.index());
                            ins2.setInt(4, slot++);
                            ins2.setString(5, o.poolId());
                            ins2.setInt(6, o.required());
                            ins2.setInt(7, o.collected());
                            ins2.addBatch();
                        }
                    }
                    ins1.executeBatch();
                    ins2.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                plugin.getLogger().severe("saveDaily failed: " + e.getMessage());
                try { connection.rollback(); } catch (SQLException ignored) {}
            } finally {
                try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        }, io);
    }

    public CompletableFuture<Void> deletePlayer(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement p1 = connection.prepareStatement("DELETE FROM players WHERE uuid=?");
                     PreparedStatement p2 = connection.prepareStatement("DELETE FROM player_daily WHERE uuid=?");
                     PreparedStatement p3 = connection.prepareStatement("DELETE FROM player_daily_objective WHERE uuid=?")) {
                    p1.setString(1, uuid.toString());
                    p2.setString(1, uuid.toString());
                    p3.setString(1, uuid.toString());
                    p1.executeUpdate();
                    p2.executeUpdate();
                    p3.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                plugin.getLogger().severe("deletePlayer failed: " + e.getMessage());
                try { connection.rollback(); } catch (SQLException ignored) {}
            } finally {
                try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        }, io);
    }

    public CompletableFuture<Void> deleteOldDailies(int keepDayKey) {
        return CompletableFuture.runAsync(() -> {
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement p1 = connection.prepareStatement("DELETE FROM player_daily WHERE day_key<?");
                     PreparedStatement p2 = connection.prepareStatement("DELETE FROM player_daily_objective WHERE day_key<?")) {
                    p1.setInt(1, keepDayKey);
                    p2.setInt(1, keepDayKey);
                    p1.executeUpdate();
                    p2.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                plugin.getLogger().severe("deleteOldDailies failed: " + e.getMessage());
                try { connection.rollback(); } catch (SQLException ignored) {}
            } finally {
                try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        }, io);
    }
}
