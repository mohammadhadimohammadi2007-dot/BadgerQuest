package ir.badgerquest.plugin;

import ir.badgerquest.plugin.bossbar.BossBarManager;
import ir.badgerquest.plugin.command.QuestCommand;
import ir.badgerquest.plugin.config.ConfigManager;
import ir.badgerquest.plugin.db.Database;
import ir.badgerquest.plugin.gui.GuiListener;
import ir.badgerquest.plugin.gui.QuestGui;
import ir.badgerquest.plugin.listener.PickupListener;
import ir.badgerquest.plugin.listener.PlayerConnectionListener;
import ir.badgerquest.plugin.placeholder.BadgerPlaceholders;
import ir.badgerquest.plugin.quest.QuestManager;
import ir.badgerquest.plugin.reset.DailyResetScheduler;
import ir.badgerquest.plugin.util.TimeService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class BadgerQuest extends JavaPlugin {

    private ConfigManager config;
    private Database database;
    private TimeService time;
    private QuestManager questManager;
    private BossBarManager bossBarManager;
    private DailyResetScheduler resetScheduler;
    private BadgerPlaceholders placeholders;

    @Override
    public void onEnable() {
        config = new ConfigManager(this);
        config.load();

        time = new TimeService(config);
        database = new Database(this);
        try {
            database.open();
        } catch (SQLException e) {
            getLogger().severe("Failed to open database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        questManager = new QuestManager(this, database, time);
        bossBarManager = new BossBarManager(this);
        resetScheduler = new DailyResetScheduler(this);
        resetScheduler.start();

        // register listeners
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new GuiListener(this), this);
        pm.registerEvents(new PickupListener(this), this);
        pm.registerEvents(new PlayerConnectionListener(this), this);

        // command
        var cmd = getCommand("quest");
        if (cmd != null) {
            QuestCommand handler = new QuestCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        // PAPI
        if (pm.getPlugin("PlaceholderAPI") != null) {
            placeholders = new BadgerPlaceholders(this);
            placeholders.register();
            getLogger().info("PlaceholderAPI hook registered.");
        }

        // Warm up already-online players (e.g. /reload)
        for (Player p : Bukkit.getOnlinePlayers()) {
            questManager.ensureLoaded(p.getUniqueId(), () -> questManager.touchSeen(p.getUniqueId()));
        }

        getLogger().info("BadgerQuest enabled.");
    }

    @Override
    public void onDisable() {
        if (resetScheduler != null) resetScheduler.stop();
        // Close any open GUIs so items aren't left in stale views
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof QuestGui) {
                p.closeInventory();
            }
        }
        if (bossBarManager != null) bossBarManager.removeAll();
        if (placeholders != null) placeholders.unregister();
        if (questManager != null) questManager.unloadAll();
        if (database != null) database.close();
        getLogger().info("BadgerQuest disabled.");
    }

    public void reloadAll() {
        // Close open GUIs first
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof QuestGui) {
                p.closeInventory();
            }
        }
        if (bossBarManager != null) bossBarManager.removeAll();
        config.load();
    }

    public ConfigManager config() { return config; }
    public Database database() { return database; }
    public TimeService time() { return time; }
    public QuestManager questManager() { return questManager; }
    public BossBarManager bossBarManager() { return bossBarManager; }
}
