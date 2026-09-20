package ir.badgerquest.plugin.gui;

import ir.badgerquest.plugin.BadgerQuest;
import ir.badgerquest.plugin.config.ConfigManager;
import ir.badgerquest.plugin.config.GuiConfig;
import ir.badgerquest.plugin.config.GuiItem;
import ir.badgerquest.plugin.quest.DailyQuests;
import ir.badgerquest.plugin.quest.Quest;
import ir.badgerquest.plugin.quest.QuestObjective;
import ir.badgerquest.plugin.util.ItemBuilder;
import ir.badgerquest.plugin.util.Text;
import ir.badgerquest.plugin.util.TimeService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single-page quest GUI. Always renders the player's currently-active quest
 * (the first non-completed one). There is no in-GUI switching between quests.
 */
public final class QuestGui implements InventoryHolder {

    private final BadgerQuest plugin;
    private final UUID viewerId;
    private final Inventory inventory;
    private final boolean openedAsAccepted;

    private QuestGui(BadgerQuest plugin, Player viewer, boolean acceptedTitle) {
        this.plugin = plugin;
        this.viewerId = viewer.getUniqueId();
        GuiConfig g = plugin.config().gui();
        String title = acceptedTitle ? g.titleAccepted() : g.title();
        this.inventory = Bukkit.createInventory(this, g.size(), Text.color(title));
        this.openedAsAccepted = acceptedTitle;
        render();
    }

    /** Opens the quest GUI on the player's active quest. */
    public static QuestGui open(BadgerQuest plugin, Player player) {
        boolean accepted = false;
        DailyQuests daily = plugin.questManager().dailyOf(player.getUniqueId());
        if (daily != null) {
            Quest q = firstActiveQuest(daily);
            accepted = q != null && q.accepted();
        }
        QuestGui gui = new QuestGui(plugin, player, accepted);
        player.openInventory(gui.getInventory());
        return gui;
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public UUID viewerId() { return viewerId; }

    /** True if the inventory was created with the "accepted" title. */
    public boolean openedAsAccepted() { return openedAsAccepted; }

    /**
     * Returns the index of the quest currently shown — the first non-completed quest
     * of the day, or -1 if all are done.
     */
    public int activeQuestIndex() {
        DailyQuests daily = plugin.questManager().dailyOf(viewerId);
        if (daily == null) return -1;
        Quest q = firstActiveQuest(daily);
        return q == null ? -1 : q.index();
    }

    public boolean isInputSlot(int rawSlot) {
        return plugin.config().gui().inputSlots().contains(rawSlot);
    }

    public boolean isAcceptSlot(int rawSlot) {
        return plugin.config().gui().acceptSlots().contains(rawSlot);
    }

    public boolean isCloseSlot(int rawSlot) {
        return plugin.config().gui().closeSlots().contains(rawSlot);
    }

    /** Returns the objective at this input slot for the active quest, or null. */
    public QuestObjective objectiveForInputSlot(int rawSlot) {
        GuiConfig gui = plugin.config().gui();
        int idx = gui.inputSlots().indexOf(rawSlot);
        if (idx < 0) return null;
        DailyQuests daily = plugin.questManager().dailyOf(viewerId);
        if (daily == null) return null;
        Quest quest = firstActiveQuest(daily);
        if (quest == null || idx >= quest.objectives().size()) return null;
        return quest.objectives().get(idx);
    }

    public int objectiveIndexForInputSlot(int rawSlot) {
        return plugin.config().gui().inputSlots().indexOf(rawSlot);
    }

    public void render() {
        ConfigManager cfg = plugin.config();
        GuiConfig gui = cfg.gui();
        int size = gui.size();

        ItemStack filler = ItemBuilder.build(gui.item("filler"), Map.of());
        for (int i = 0; i < size; i++) inventory.setItem(i, filler);
        for (int empty : gui.emptySlots()) {
            if (empty >= 0 && empty < size) inventory.setItem(empty, null);
        }

        DailyQuests daily = plugin.questManager().dailyOf(viewerId);
        if (daily == null) return;

        boolean allDone = daily.allCompleted();
        Quest quest = firstActiveQuest(daily);

        setAll(gui.closeSlots(), size, ItemBuilder.build(gui.item("close_button"), Map.of()));

        Map<String, String> globals = globalPlaceholders(daily, quest);

        setAll(gui.streakSlots(), size, ItemBuilder.build(gui.item("streak_indicator"), globals));
        setAll(gui.infoSlots(), size, ItemBuilder.build(gui.item("info_indicator"), globals));

        if (!gui.questInfoSlots().isEmpty()) {
            setAll(gui.questInfoSlots(), size, ItemBuilder.build(gui.item("quest_info"), globals));
        }

        if (allDone) {
            setAll(gui.acceptSlots(), size, ItemBuilder.build(gui.item("all_done_marker"), globals));
            return; // no input slots to render when everything is done
        }

        if (quest == null) return;

        // display slots for objectives
        List<Integer> displaySlots = gui.displaySlots();
        List<Integer> inputSlots = gui.inputSlots();
        for (int i = 0; i < quest.objectives().size() && i < displaySlots.size(); i++) {
            QuestObjective o = quest.objectives().get(i);
            int slot = displaySlots.get(i);
            if (slot < 0 || slot >= size) continue;
            inventory.setItem(slot, buildDisplay(o));
        }

        if (!quest.accepted()) {
            setAll(gui.acceptSlots(), size, ItemBuilder.build(gui.item("accept_button"), globals));
            for (int slot : inputSlots) {
                if (slot >= 0 && slot < size) inventory.setItem(slot, filler);
            }
        } else {
            setAll(gui.acceptSlots(), size, ItemBuilder.build(gui.item("accepted_marker"), globals));
            for (int i = 0; i < inputSlots.size(); i++) {
                int slot = inputSlots.get(i);
                if (slot < 0 || slot >= size) continue;
                QuestObjective o = i < quest.objectives().size() ? quest.objectives().get(i) : null;
                if (o != null && o.isComplete()) {
                    inventory.setItem(slot, filler);
                } else {
                    inventory.setItem(slot, ItemBuilder.build(gui.item("input_slot_hint"), Map.of()));
                }
            }
        }
    }

    private void setAll(List<Integer> slots, int size, ItemStack item) {
        if (slots == null) return;
        for (int slot : slots) {
            if (slot >= 0 && slot < size) inventory.setItem(slot, item);
        }
    }

    private ItemStack buildDisplay(QuestObjective o) {
        ConfigManager cfg = plugin.config();
        GuiConfig gui = cfg.gui();
        Map<String, String> ph = new HashMap<>();
        ph.put("item", Text.color(o.entry().displayName()));
        ph.put("have", String.valueOf(o.collected()));
        ph.put("need", String.valueOf(o.required()));
        ph.put("remaining", String.valueOf(o.remaining()));

        if (o.isComplete()) {
            return ItemBuilder.build(gui.item("display_completed"), ph);
        }
        GuiItem tpl = gui.item("display_progress");
        Material mat = o.entry().material();
        int cmd = o.entry().customModelData();
        ItemStack stack = ItemBuilder.build(mat, cmd, tpl.name(), tpl.lore(), ph);
        int amount = Math.max(1, Math.min(64, o.remaining()));
        stack.setAmount(amount);
        return stack;
    }

    private Map<String, String> globalPlaceholders(DailyQuests daily, Quest activeQuest) {
        ConfigManager cfg = plugin.config();
        var rec = plugin.questManager().playerOf(viewerId);
        int streak = rec != null ? rec.streak : 0;
        int highscore = rec != null ? rec.highscore : 0;

        int daysUntilStreakReset;
        TimeService time = plugin.time();
        if (rec != null && rec.lastSeenDay > 0) {
            long absence = time.daysBetween(rec.lastSeenDay, time.currentDayKey());
            daysUntilStreakReset = (int) Math.max(0, cfg.streakResetAfterDays() - absence);
        } else {
            daysUntilStreakReset = cfg.streakResetAfterDays();
        }

        int nextMilestoneDays = -1;
        String nextMilestoneName = "None";
        int best = Integer.MAX_VALUE;
        for (Integer day : cfg.streakMilestoneRewards().keySet()) {
            if (day > streak) {
                int distance = day - streak;
                if (distance < best) {
                    best = distance;
                    nextMilestoneDays = distance;
                    nextMilestoneName = "Streak " + day;
                }
            }
        }

        String indexStr = activeQuest == null ? "-" : String.valueOf(activeQuest.index());

        Map<String, String> ph = new HashMap<>();
        ph.put("streak", String.valueOf(streak));
        ph.put("highscore", String.valueOf(highscore));
        ph.put("days_until_reset", Text.formatDuration(plugin.time().secondsUntilNextReset()));
        ph.put("days_until_streak_reset", String.valueOf(daysUntilStreakReset));
        ph.put("time_until_reset", Text.formatDuration(plugin.time().secondsUntilNextReset()));
        ph.put("index", indexStr);
        ph.put("total_quests", String.valueOf(cfg.questsPerDay()));
        ph.put("completed_today", String.valueOf(daily.completedCount()));
        ph.put("next_milestone_days", nextMilestoneDays < 0 ? "-" : String.valueOf(nextMilestoneDays));
        ph.put("next_milestone_name", nextMilestoneName);
        return ph;
    }

    private static Quest firstActiveQuest(DailyQuests daily) {
        for (Quest q : daily.quests()) if (!q.completed()) return q;
        return null;
    }
}
