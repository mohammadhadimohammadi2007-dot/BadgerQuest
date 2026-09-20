package ir.badgerquest.plugin.config;

import ir.badgerquest.plugin.BadgerQuest;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConfigManager {

    private final BadgerQuest plugin;

    private int questsPerDay;
    private int itemsPerQuest;
    private int resetHour;
    private ZoneId zoneId;

    private int streakResetAfterDays;
    private int streakMax;
    private Map<Integer, List<String>> streakMilestoneRewards;

    private boolean bossbarEnabled;
    private int bossbarFadeSeconds;
    private BarColor bossbarColor;
    private BarStyle bossbarStyle;
    private String bossbarTitle;

    private List<PoolEntry> pool;
    private Map<String, PoolEntry> poolById;

    private Map<Integer, List<String>> questRewards;
    private List<String> defaultQuestReward;
    private List<String> dailyCompletionReward;

    private Map<String, String> messages;
    private String prefix;

    private GuiConfig gui;
    private String databaseFile;

    public ConfigManager(BadgerQuest plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        questsPerDay = Math.max(1, c.getInt("quests_per_day", 7));
        itemsPerQuest = Math.max(1, c.getInt("items_per_quest", 3));
        resetHour = Math.floorMod(c.getInt("reset_hour", 2), 24);

        String tz = c.getString("timezone", "system");
        try {
            zoneId = "system".equalsIgnoreCase(tz) ? ZoneId.systemDefault() : ZoneId.of(tz);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid timezone '" + tz + "', falling back to system default.");
            zoneId = ZoneId.systemDefault();
        }

        streakResetAfterDays = Math.max(1, c.getInt("streak.reset_after_days", 7));
        streakMax = Math.max(0, c.getInt("streak.max", 30));
        streakMilestoneRewards = new HashMap<>();
        ConfigurationSection msec = c.getConfigurationSection("streak.milestone_rewards");
        if (msec != null) {
            for (String key : msec.getKeys(false)) {
                try {
                    int day = Integer.parseInt(key);
                    streakMilestoneRewards.put(day, msec.getStringList(key));
                } catch (NumberFormatException ignored) {}
            }
        }

        bossbarEnabled = c.getBoolean("bossbar.enabled", true);
        bossbarFadeSeconds = Math.max(1, c.getInt("bossbar.fade_seconds", 4));
        bossbarColor = parseEnum(BarColor.class, c.getString("bossbar.color", "YELLOW"), BarColor.YELLOW);
        bossbarStyle = parseEnum(BarStyle.class, c.getString("bossbar.style", "SOLID"), BarStyle.SOLID);
        bossbarTitle = c.getString("bossbar.title", "&e{item} &7{have}/{need}");

        loadPool(c);
        loadQuestRewards(c);
        loadMessages(c);
        loadGui(c);

        databaseFile = c.getString("database.file", "badgerquest.db");
    }

    private void loadPool(FileConfiguration c) {
        pool = new ArrayList<>();
        poolById = new HashMap<>();
        List<Map<?, ?>> raw = c.getMapList("item_pool");
        for (Map<?, ?> m : raw) {
            String id = str(m.get("id"));
            Material mat = parseEnum(Material.class, str(m.get("material")), null);
            if (id == null || mat == null) {
                plugin.getLogger().warning("Skipping invalid item_pool entry: " + m);
                continue;
            }
            int cmd = intOr(m.get("custom_model_data"), -1);
            String name = str(m.get("display_name"));
            if (name == null || name.isEmpty()) name = mat.name();
            int min = Math.max(1, intOr(m.get("min"), 1));
            int max = Math.max(min, intOr(m.get("max"), min));
            PoolEntry e = new PoolEntry(id, mat, cmd, name, min, max);
            pool.add(e);
            poolById.put(id, e);
        }
        if (pool.isEmpty()) {
            plugin.getLogger().severe("item_pool is empty! No quests can be generated.");
        } else if (pool.size() < itemsPerQuest) {
            plugin.getLogger().warning("item_pool has fewer entries than items_per_quest. Duplicates within a quest will occur.");
        }
    }

    private void loadQuestRewards(FileConfiguration c) {
        questRewards = new HashMap<>();
        defaultQuestReward = List.of();
        ConfigurationSection sec = c.getConfigurationSection("quest_rewards");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                if ("default".equalsIgnoreCase(key)) {
                    defaultQuestReward = sec.getStringList(key);
                } else {
                    try {
                        int idx = Integer.parseInt(key);
                        questRewards.put(idx, sec.getStringList(key));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        dailyCompletionReward = c.getStringList("daily_completion_reward");
    }

    private void loadMessages(FileConfiguration c) {
        messages = new HashMap<>();
        ConfigurationSection sec = c.getConfigurationSection("messages");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                messages.put(k, sec.getString(k, ""));
            }
        }
        prefix = messages.getOrDefault("prefix", "");
    }

    private void loadGui(FileConfiguration c) {
        ConfigurationSection g = c.getConfigurationSection("gui");
        if (g == null) throw new IllegalStateException("Missing 'gui' section in config.yml");

        int size = g.getInt("size", 54);
        if (size % 9 != 0 || size < 9 || size > 54) size = 54;

        List<Integer> inputSlots = g.getIntegerList("input_slots");
        List<Integer> displaySlots = g.getIntegerList("display_slots");
        if (inputSlots.size() != itemsPerQuest || displaySlots.size() != itemsPerQuest) {
            plugin.getLogger().severe("gui.input_slots and gui.display_slots must both have exactly items_per_quest entries.");
        }

        Map<String, GuiItem> items = new LinkedHashMap<>();
        ConfigurationSection isec = g.getConfigurationSection("items");
        if (isec != null) {
            for (String key : isec.getKeys(false)) {
                ConfigurationSection s = isec.getConfigurationSection(key);
                if (s == null) continue;
                Material mat = parseEnum(Material.class, s.getString("material", "STONE"), Material.STONE);
                int cmd = s.getInt("custom_model_data", -1);
                String name = s.getString("name", " ");
                List<String> lore = s.getStringList("lore");
                items.put(key, new GuiItem(mat, cmd, name, lore));
            }
        }

        String rawTitle = g.getString("title", "&8Badger's Cabin");
        String rawAccepted = g.getString("title_accepted", rawTitle);

        gui = new GuiConfig(
                rawTitle,
                rawAccepted,
                size,
                Collections.unmodifiableList(inputSlots),
                Collections.unmodifiableList(displaySlots),
                Collections.unmodifiableList(slotList(g, "accept_slot", List.of(4))),
                Collections.unmodifiableList(slotList(g, "quest_info_slot", List.of(49))),
                Collections.unmodifiableList(slotList(g, "streak_slot", List.of(40))),
                Collections.unmodifiableList(slotList(g, "info_slot", List.of(42))),
                Collections.unmodifiableList(slotList(g, "close_slot", List.of(48))),
                g.getIntegerList("empty_slots"),
                items
        );
    }

    /**
     * Reads a slot config that may be either a single int OR a list of ints.
     * Returns the defaults if the key is missing or negative-only.
     */
    private static List<Integer> slotList(ConfigurationSection g, String key, List<Integer> def) {
        if (!g.isSet(key)) return def;
        if (g.isList(key)) {
            List<Integer> list = g.getIntegerList(key);
            return list.isEmpty() ? def : list;
        }
        int v = g.getInt(key, -1);
        if (v < 0) return List.of();
        return List.of(v);
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> cls, String value, T def) {
        if (value == null) return def;
        try {
            return Enum.valueOf(cls, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static int intOr(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
        }
        return def;
    }

    // --- getters --------------------------------------------------------------
    public int questsPerDay() { return questsPerDay; }
    public int itemsPerQuest() { return itemsPerQuest; }
    public int resetHour() { return resetHour; }
    public ZoneId zoneId() { return zoneId; }
    public int streakResetAfterDays() { return streakResetAfterDays; }
    public int streakMax() { return streakMax; }
    public Map<Integer, List<String>> streakMilestoneRewards() { return streakMilestoneRewards; }
    public boolean bossbarEnabled() { return bossbarEnabled; }
    public int bossbarFadeSeconds() { return bossbarFadeSeconds; }
    public BarColor bossbarColor() { return bossbarColor; }
    public BarStyle bossbarStyle() { return bossbarStyle; }
    public String bossbarTitle() { return bossbarTitle; }
    public List<PoolEntry> pool() { return pool; }
    public PoolEntry poolEntry(String id) { return poolById.get(id); }
    public List<String> questReward(int index) {
        return questRewards.getOrDefault(index, defaultQuestReward);
    }
    public List<String> dailyCompletionReward() { return dailyCompletionReward; }
    public String message(String key) { return messages.getOrDefault(key, ""); }
    public String prefix() { return prefix; }
    public GuiConfig gui() { return gui; }
    public String databaseFile() { return databaseFile; }
}
