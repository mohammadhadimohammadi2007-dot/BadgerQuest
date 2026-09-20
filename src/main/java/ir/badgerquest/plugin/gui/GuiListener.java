package ir.badgerquest.plugin.gui;

import ir.badgerquest.plugin.BadgerQuest;
import ir.badgerquest.plugin.quest.QuestObjective;
import ir.badgerquest.plugin.util.ItemMatcher;
import ir.badgerquest.plugin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GuiListener implements Listener {

    private final BadgerQuest plugin;

    public GuiListener(BadgerQuest plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        Inventory top = view.getTopInventory();
        if (!(top.getHolder() instanceof QuestGui gui)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!gui.viewerId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        // Creative-mode packet safety.
        if (player.getGameMode() == GameMode.CREATIVE
                && event.getClick() == ClickType.CREATIVE) {
            event.setCancelled(true);
            player.updateInventory();
            return;
        }

        int raw = event.getRawSlot();
        int topSize = top.getSize();
        boolean inTop = raw >= 0 && raw < topSize;
        InventoryAction action = event.getAction();
        ClickType click = event.getClick();

        // A number-key press on a bottom slot swaps that slot with the top's hotbar-target
        // slot if inventory allows; here it targets the player hotbar so it stays in bottom.
        // Only NUMBER_KEY / SWAP_OFFHAND clicked while cursor is over a TOP slot moves items.
        // We block any cross-inventory action outright.

        synchronized (plugin.questManager().lockFor(player.getUniqueId())) {
            if (inTop) {
                event.setCancelled(true);
                event.setResult(org.bukkit.event.Event.Result.DENY);
                handleTopClick(gui, player, event, raw);
                player.updateInventory();
                return;
            }
            // Bottom-inventory click. Only intercept if it would touch the top inventory.
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);
                event.setResult(org.bukkit.event.Event.Result.DENY);
                depositFromPlayerSlot(gui, player, event.getSlot());
                player.updateInventory();
                return;
            }
            if (action == InventoryAction.COLLECT_TO_CURSOR) {
                // Double-click gathers from BOTH inventories — block to keep top pristine.
                event.setCancelled(true);
                event.setResult(org.bukkit.event.Event.Result.DENY);
                player.updateInventory();
                return;
            }
            // Anything else — normal player-inventory manipulation: allow it through.
        }
    }

    private void handleTopClick(QuestGui gui, Player player, InventoryClickEvent event, int raw) {
        if (gui.isCloseSlot(raw)) {
            Bukkit.getScheduler().runTask(plugin, (Runnable) player::closeInventory);
            return;
        }
        if (gui.isAcceptSlot(raw)) {
            int idx = gui.activeQuestIndex();
            if (idx < 0) return;
            if (plugin.questManager().acceptQuest(player.getUniqueId(), idx)) {
                sendPrefixed(player, plugin.config().message("quest_accepted"));
                // Re-open so the title flips to the accepted variant.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    QuestGui.open(plugin, player);
                });
            }
            return;
        }
        if (gui.isInputSlot(raw)) {
            depositFromCursor(gui, player, event, raw);
            return;
        }
    }

    private void depositFromCursor(QuestGui gui, Player player, InventoryClickEvent event, int rawSlot) {
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType().isAir()) return;

        int questIndex = gui.activeQuestIndex();
        if (questIndex < 0) return;

        QuestObjective o = gui.objectiveForInputSlot(rawSlot);
        if (o == null || o.isComplete()) return;

        if (!ItemMatcher.matches(cursor, o.entry().material(), o.entry().customModelData())) {
            sendPrefixed(player, plugin.config().message("wrong_item"));
            return;
        }

        int slot = gui.objectiveIndexForInputSlot(rawSlot);
        int available = cursor.getAmount();
        int wanted = o.remaining();
        int toTake = Math.min(available, wanted);
        if (toTake <= 0) return;

        int used = plugin.questManager().consumeIntoSlot(player, questIndex, slot, toTake);
        if (used <= 0) return;

        int remaining = available - used;
        if (remaining <= 0) {
            event.getView().setCursor(null);
        } else {
            ItemStack newCursor = cursor.clone();
            newCursor.setAmount(remaining);
            event.getView().setCursor(newCursor);
        }
        afterDeposit(player, gui, o, used);
    }

    private void depositFromPlayerSlot(QuestGui gui, Player player, int playerSlot) {
        PlayerInventory pi = player.getInventory();
        ItemStack stack = pi.getItem(playerSlot);
        if (stack == null || stack.getType().isAir()) return;

        int questIndex = gui.activeQuestIndex();
        if (questIndex < 0) return;

        var daily = plugin.questManager().dailyOf(player.getUniqueId());
        if (daily == null) return;
        var quest = daily.get(questIndex);
        if (quest == null || !quest.accepted() || quest.completed()) return;

        for (int i = 0; i < quest.objectives().size(); i++) {
            QuestObjective o = quest.objectives().get(i);
            if (o.isComplete()) continue;
            if (!ItemMatcher.matches(stack, o.entry().material(), o.entry().customModelData())) continue;
            int available = stack.getAmount();
            int wanted = o.remaining();
            int toTake = Math.min(available, wanted);
            if (toTake <= 0) continue;
            int used = plugin.questManager().consumeIntoSlot(player, questIndex, i, toTake);
            if (used <= 0) continue;
            int newAmount = available - used;
            if (newAmount <= 0) {
                pi.setItem(playerSlot, null);
            } else {
                ItemStack copy = stack.clone();
                copy.setAmount(newAmount);
                pi.setItem(playerSlot, copy);
            }
            afterDeposit(player, gui, o, used);
            return;
        }
    }

    private void afterDeposit(Player player, QuestGui gui, QuestObjective o, int used) {
        Map<String, String> ph = new HashMap<>();
        ph.put("have", String.valueOf(o.collected()));
        ph.put("total", String.valueOf(o.required()));
        ph.put("item", Text.color(o.entry().displayName()));
        if (o.isComplete()) {
            String msg = plugin.config().message("quest_step_completed");
            if (msg != null && !msg.isEmpty()) sendPrefixed(player, Text.apply(msg, ph));
        }
        gui.render();

        var daily = plugin.questManager().dailyOf(player.getUniqueId());
        if (daily == null) return;
        // After a deposit, if the objective just filled up, check whether the *quest itself*
        // completed on this deposit. We look at the quest that owned this objective.
        ir.badgerquest.plugin.quest.Quest q = null;
        for (ir.badgerquest.plugin.quest.Quest cand : daily.quests()) {
            if (cand.objectives().contains(o)) { q = cand; break; }
        }
        if (q == null || !q.completed()) return;

        Map<String, String> qp = new HashMap<>();
        qp.put("index", String.valueOf(q.index()));
        sendPrefixed(player, Text.apply(plugin.config().message("quest_completed"), qp));
        boolean allDone = daily.allCompleted();
        if (allDone) sendPrefixed(player, plugin.config().message("all_quests_done"));

        // Close GUI FIRST, then dispatch rewards on the next tick.
        final int qIndex = q.index();
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.questManager().dispatchQuestRewards(player, qIndex);
                if (allDone) plugin.questManager().dispatchDailyCompletion(player);
            });
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        InventoryView view = event.getView();
        if (!(view.getTopInventory().getHolder() instanceof QuestGui)) return;
        // Cancel drags that touch any top slot; drags entirely in the bottom are allowed.
        int topSize = view.getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                event.setResult(org.bukkit.event.Event.Result.DENY);
                if (event.getWhoClicked() instanceof Player p) p.updateInventory();
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof QuestGui)) return;
        if (event.getPlayer() instanceof Player p) {
            UUID id = p.getUniqueId();
            plugin.questManager().touchSeen(id);
        }
    }

    private void sendPrefixed(Player player, String message) {
        if (message == null || message.isEmpty()) return;
        player.sendMessage(Text.color(plugin.config().prefix() + message));
    }
}
