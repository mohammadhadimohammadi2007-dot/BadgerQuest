package ir.badgerquest.plugin.reset;

import ir.badgerquest.plugin.BadgerQuest;
import ir.badgerquest.plugin.gui.QuestGui;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class DailyResetScheduler {

    private final BadgerQuest plugin;
    private BukkitTask task;
    private int lastRunDayKey = -1;

    public DailyResetScheduler(BadgerQuest plugin) {
        this.plugin = plugin;
    }

    public void start() {
        lastRunDayKey = plugin.time().currentDayKey();
        // Check every 30 seconds whether the day-key rolled over. This is cheap and precise
        // to within 30s of the configured reset_hour.
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L * 30, 20L * 30);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        int now = plugin.time().currentDayKey();
        if (now != lastRunDayKey) {
            lastRunDayKey = now;
            fireReset();
        }
    }

    private void fireReset() {
        plugin.getLogger().info("Daily quest reset triggered.");
        // close any open GUIs
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof QuestGui) {
                p.closeInventory();
            }
        }
        plugin.bossBarManager().removeAll();
        plugin.questManager().handleDailyReset();
    }
}
