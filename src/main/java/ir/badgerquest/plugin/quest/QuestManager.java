package ir.badgerquest.plugin.quest;

import ir.badgerquest.plugin.BadgerQuest;
import ir.badgerquest.plugin.db.Database;
import ir.badgerquest.plugin.db.PlayerRecord;
import ir.badgerquest.plugin.util.TimeService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central per-player state cache. Guards operations with a per-player lock so
 * concurrent GUI clicks + pickup listeners cannot race and cause double-consume.
 */
public final class QuestManager {

    private final BadgerQuest plugin;
    private final Database db;
    private final TimeService time;
    private final QuestGenerator generator;

    private final Map<UUID, DailyQuests> dailyCache = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerRecord> playerCache = new ConcurrentHashMap<>();
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();

    public QuestManager(BadgerQuest plugin, Database db, TimeService time) {
        this.plugin = plugin;
        this.db = db;
        this.time = time;
        this.generator = new QuestGenerator(plugin.config());
    }

    public Object lockFor(UUID uuid) {
        return locks.computeIfAbsent(uuid, k -> new Object());
    }

    /**
     * Ensures a player is loaded. Returns a future completed on the plugin's async pool.
     */
    public void ensureLoaded(UUID uuid, Runnable onReady) {
        if (playerCache.containsKey(uuid) && dailyCache.containsKey(uuid)
                && dailyCache.get(uuid).dayKey() == time.currentDayKey()) {
            if (onReady != null) onReady.run();
            return;
        }
        int today = time.currentDayKey();
        db.loadPlayer(uuid).thenCompose(pr -> {
            playerCache.put(uuid, pr);
            return db.loadDaily(uuid, today).thenAccept(existing -> {
                if (existing != null) {
                    dailyCache.put(uuid, existing);
                } else {
                    DailyQuests fresh = generator.generate(uuid, today);
                    dailyCache.put(uuid, fresh);
                    db.saveDaily(fresh);
                }
            });
        }).whenComplete((v, ex) -> {
            if (ex != null) {
                plugin.getLogger().severe("ensureLoaded failed for " + uuid + ": " + ex.getMessage());
            }
            if (onReady != null) {
                // marshal callback back to main thread
                Bukkit.getScheduler().runTask(plugin, onReady);
            }
        });
    }

    public DailyQuests dailyOf(UUID uuid) {
        return dailyCache.get(uuid);
    }

    public PlayerRecord playerOf(UUID uuid) {
        return playerCache.get(uuid);
    }

    /**
     * Called by daily-reset scheduler. Regenerates all cached players' quests for the new day.
     * Also updates streak for players who did NOT complete yesterday.
     */
    public void handleDailyReset() {
        int newDay = time.currentDayKey();
        // For every cached player: evaluate streak based on completion status, then regenerate.
        for (UUID uuid : dailyCache.keySet()) {
            synchronized (lockFor(uuid)) {
                DailyQuests old = dailyCache.get(uuid);
                PlayerRecord rec = playerCache.get(uuid);
                if (rec == null) rec = new PlayerRecord();

                if (old != null && !old.allCompleted()) {
                    // failed to complete previous day → streak resets
                    if (rec.lastCompletionDay != old.dayKey()) {
                        rec.streak = 0;
                    }
                }

                DailyQuests fresh = generator.generate(uuid, newDay);
                dailyCache.put(uuid, fresh);
                playerCache.put(uuid, rec);
                db.savePlayer(uuid, rec);
                db.saveDaily(fresh);
            }
        }
        db.deleteOldDailies(newDay);
    }

    /**
     * Tries to consume up to `amount` of the given pool item into any matching objective
     * of the given quest. Returns the number actually consumed.
     * Marks the quest completed but does NOT dispatch rewards — callers must invoke
     * {@link #dispatchQuestRewards} / {@link #dispatchDailyCompletion} after closing any open GUI.
     * Must be called under the player's lock.
     */
    public int consumeInto(Player player, int questIndex, String poolId, int amount) {
        DailyQuests daily = dailyCache.get(player.getUniqueId());
        if (daily == null) return 0;
        Quest quest = daily.get(questIndex);
        if (quest == null || !quest.accepted() || quest.completed()) return 0;

        int totalConsumed = 0;
        for (QuestObjective o : quest.objectives()) {
            if (totalConsumed >= amount) break;
            if (!o.poolId().equals(poolId)) continue;
            int used = o.consume(amount - totalConsumed);
            totalConsumed += used;
        }

        if (totalConsumed > 0) {
            if (quest.allObjectivesComplete() && !quest.completed()) {
                quest.markCompleted();
            }
            db.saveDaily(daily);
        }
        return totalConsumed;
    }

    /** Marks an objective at a specific slot (0-indexed) — variant used by GUI where slot matters. */
    public int consumeIntoSlot(Player player, int questIndex, int objectiveSlot, int amount) {
        DailyQuests daily = dailyCache.get(player.getUniqueId());
        if (daily == null) return 0;
        Quest quest = daily.get(questIndex);
        if (quest == null || !quest.accepted() || quest.completed()) return 0;
        if (objectiveSlot < 0 || objectiveSlot >= quest.objectives().size()) return 0;
        QuestObjective o = quest.objectives().get(objectiveSlot);
        int used = o.consume(amount);
        if (used > 0) {
            if (quest.allObjectivesComplete() && !quest.completed()) {
                quest.markCompleted();
            }
            db.saveDaily(daily);
        }
        return used;
    }

    /** Runs the per-quest reward commands. Safe to call from main thread. */
    public void dispatchQuestRewards(Player player, int questIndex) {
        runQuestRewards(player, questIndex);
    }

    /** Runs the daily-completion + streak reward chain. Safe to call from main thread. */
    public void dispatchDailyCompletion(Player player) {
        DailyQuests daily = dailyCache.get(player.getUniqueId());
        if (daily == null) return;
        handleAllDailyComplete(player, daily);
    }

    public boolean acceptQuest(UUID uuid, int questIndex) {
        DailyQuests daily = dailyCache.get(uuid);
        if (daily == null) return false;
        Quest quest = daily.get(questIndex);
        if (quest == null || quest.accepted() || quest.completed()) return false;
        quest.accept(System.currentTimeMillis());
        db.saveDaily(daily);
        return true;
    }

    private void runQuestRewards(Player player, int questIndex) {
        List<String> cmds = plugin.config().questReward(questIndex);
        dispatchCommands(player, cmds);
    }

    private void handleAllDailyComplete(Player player, DailyQuests daily) {
        // streak logic
        UUID uuid = player.getUniqueId();
        PlayerRecord rec = playerCache.computeIfAbsent(uuid, k -> new PlayerRecord());
        int today = daily.dayKey();
        long gap = time.daysBetween(rec.lastCompletionDay, today);
        if (rec.lastCompletionDay == 0 || gap > 1) {
            rec.streak = 1;
        } else if (gap == 1) {
            rec.streak += 1;
        } // gap == 0 (same day) shouldn't happen since we only enter here on completion of today's set
        int cap = plugin.config().streakMax();
        if (cap > 0 && rec.streak > cap) rec.streak = cap;
        if (rec.streak > rec.highscore) rec.highscore = rec.streak;
        rec.lastCompletionDay = today;
        rec.lastSeenDay = today;
        db.savePlayer(uuid, rec);

        dispatchCommands(player, plugin.config().dailyCompletionReward());
        List<String> milestone = plugin.config().streakMilestoneRewards().get(rec.streak);
        if (milestone != null && !milestone.isEmpty()) {
            dispatchCommands(player, milestone);
        }
    }

    public void touchSeen(UUID uuid) {
        PlayerRecord rec = playerCache.computeIfAbsent(uuid, k -> new PlayerRecord());
        int today = time.currentDayKey();
        if (rec.lastSeenDay != today) {
            // absence-based streak reset
            long absence = rec.lastSeenDay == 0 ? 0 : time.daysBetween(rec.lastSeenDay, today);
            if (absence > plugin.config().streakResetAfterDays()) {
                rec.streak = 0;
            }
            rec.lastSeenDay = today;
            db.savePlayer(uuid, rec);
        }
    }

    private void dispatchCommands(Player player, List<String> cmds) {
        if (cmds == null || cmds.isEmpty()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String raw : cmds) {
                String cmd = raw.replace("%player%", player.getName())
                                .replace("%uuid%", player.getUniqueId().toString());
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Reward command failed: " + cmd + " → " + ex.getMessage());
                }
            }
        });
    }

    public void unloadPlayer(UUID uuid) {
        DailyQuests d = dailyCache.remove(uuid);
        PlayerRecord r = playerCache.remove(uuid);
        locks.remove(uuid);
        if (d != null) db.saveDaily(d);
        if (r != null) db.savePlayer(uuid, r);
    }

    public void unloadAll() {
        for (UUID u : dailyCache.keySet()) unloadPlayer(u);
    }
}
