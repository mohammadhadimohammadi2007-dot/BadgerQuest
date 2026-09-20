package ir.badgerquest.plugin.bossbar;

import ir.badgerquest.plugin.BadgerQuest;
import ir.badgerquest.plugin.config.ConfigManager;
import ir.badgerquest.plugin.quest.DailyQuests;
import ir.badgerquest.plugin.quest.Quest;
import ir.badgerquest.plugin.quest.QuestObjective;
import ir.badgerquest.plugin.util.ItemMatcher;
import ir.badgerquest.plugin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BossBarManager {

    private final BadgerQuest plugin;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> hideTasks = new ConcurrentHashMap<>();
    /** Snapshot of matching-item counts in a player's inventory keyed by pool id. */
    private final Map<UUID, Map<String, Integer>> lastCounts = new ConcurrentHashMap<>();
    /** True if we owe this player a scan the moment they close their next inventory. */
    private final Map<UUID, Boolean> pending = new ConcurrentHashMap<>();

    public BossBarManager(BadgerQuest plugin) {
        this.plugin = plugin;
    }

    /**
     * Scan the player's inventory. If any active objective now has MORE matching items in
     * inventory than the last snapshot, show the bar for that objective. If an external
     * inventory is open, defer the show until it closes.
     */
    public void scan(Player player) {
        if (player == null || !player.isOnline()) return;
        ConfigManager cfg = plugin.config();
        if (!cfg.bossbarEnabled()) return;

        UUID id = player.getUniqueId();
        DailyQuests daily = plugin.questManager().dailyOf(id);
        if (daily == null) return;

        Map<String, Integer> prev = lastCounts.computeIfAbsent(id, k -> new HashMap<>());
        QuestObjective toShow = null;

        for (Quest q : daily.quests()) {
            if (!q.accepted() || q.completed()) continue;
            for (QuestObjective o : q.objectives()) {
                if (o.isComplete()) continue;
                int inv = countInInventory(player, o);
                Integer old = prev.get(o.poolId());
                if (toShow == null && (old == null || inv > old)) {
                    toShow = o;
                }
                prev.put(o.poolId(), inv);
            }
        }

        if (toShow == null) return;

        if (isExternalInventoryOpen(player)) {
            pending.put(id, true);
            return;
        }
        pending.remove(id);
        showFor(player, toShow);
    }

    /** Called from InventoryCloseEvent to flush any deferred bossbar update. */
    public void flushOnClose(Player player) {
        if (player == null || !player.isOnline()) return;
        if (pending.remove(player.getUniqueId()) == null) return;
        // Recompute now that inventory is closed — the top choice may have changed.
        UUID id = player.getUniqueId();
        DailyQuests daily = plugin.questManager().dailyOf(id);
        if (daily == null) return;
        for (Quest q : daily.quests()) {
            if (!q.accepted() || q.completed()) continue;
            for (QuestObjective o : q.objectives()) {
                if (o.isComplete()) continue;
                if (countInInventory(player, o) > 0) {
                    showFor(player, o);
                    return;
                }
            }
        }
    }

    /** Immediate show; caller decided the objective is relevant. */
    public void showFor(Player player, QuestObjective objective) {
        ConfigManager cfg = plugin.config();
        if (!cfg.bossbarEnabled()) return;

        UUID id = player.getUniqueId();
        BossBar bar = bars.get(id);
        if (bar == null) {
            bar = Bukkit.createBossBar(" ", cfg.bossbarColor(), cfg.bossbarStyle());
            bar.addPlayer(player);
            bars.put(id, bar);
        }

        int have = objective.collected();
        int need = Math.max(1, objective.required());
        int invCount = countInInventory(player, objective);
        int projected = Math.min(need, have + invCount);

        Map<String, String> ph = new HashMap<>();
        ph.put("item", Text.color(objective.entry().displayName()));
        ph.put("have", String.valueOf(have));
        ph.put("need", String.valueOf(need));
        ph.put("remaining", String.valueOf(objective.remaining()));
        ph.put("inv", String.valueOf(invCount));
        ph.put("projected", String.valueOf(projected));

        bar.setTitle(Text.color(Text.apply(cfg.bossbarTitle(), ph)));
        bar.setProgress(Math.max(0.0, Math.min(1.0, (double) projected / (double) need)));
        bar.setVisible(true);

        BukkitTask prev = hideTasks.remove(id);
        if (prev != null) prev.cancel();
        BukkitTask hide = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            BossBar b = bars.get(id);
            if (b != null) b.setVisible(false);
            hideTasks.remove(id);
        }, cfg.bossbarFadeSeconds() * 20L);
        hideTasks.put(id, hide);
    }

    private boolean isExternalInventoryOpen(Player player) {
        InventoryType top = player.getOpenInventory().getTopInventory().getType();
        return top != InventoryType.CRAFTING && top != InventoryType.CREATIVE;
    }

    private int countInInventory(Player player, QuestObjective objective) {
        PlayerInventory pi = player.getInventory();
        int total = 0;
        for (ItemStack s : pi.getContents()) {
            if (s == null) continue;
            if (ItemMatcher.matches(s, objective.entry().material(), objective.entry().customModelData())) {
                total += s.getAmount();
            }
        }
        return total;
    }

    public void remove(Player player) {
        UUID id = player.getUniqueId();
        BossBar b = bars.remove(id);
        if (b != null) {
            b.removeAll();
            b.setVisible(false);
        }
        BukkitTask t = hideTasks.remove(id);
        if (t != null) t.cancel();
        lastCounts.remove(id);
        pending.remove(id);
    }

    public void removeAll() {
        for (BossBar b : bars.values()) {
            b.removeAll();
            b.setVisible(false);
        }
        bars.clear();
        for (BukkitTask t : hideTasks.values()) t.cancel();
        hideTasks.clear();
        lastCounts.clear();
        pending.clear();
    }
}
