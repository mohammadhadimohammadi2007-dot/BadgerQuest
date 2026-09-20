package ir.badgerquest.plugin.listener;

import ir.badgerquest.plugin.BadgerQuest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

public final class PickupListener implements Listener {

    private final BadgerQuest plugin;

    public PickupListener(BadgerQuest plugin) {
        this.plugin = plugin;
    }

    private void schedule(Player player) {
        if (player == null) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) plugin.bossBarManager().scan(player);
        }, 1L);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        schedule(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        schedule(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Skip clicks in our own quest GUI — GuiListener handles bossbar there itself.
        InventoryView view = event.getView();
        Inventory top = view.getTopInventory();
        if (top.getHolder() instanceof ir.badgerquest.plugin.gui.QuestGui) return;

        // Only fire when the action can add items to player inventory.
        InventoryAction action = event.getAction();
        ClickType click = event.getClick();
        boolean interesting =
                action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.PICKUP_ALL
                || action == InventoryAction.PICKUP_HALF
                || action == InventoryAction.PICKUP_ONE
                || action == InventoryAction.PICKUP_SOME
                || action == InventoryAction.PLACE_ALL
                || action == InventoryAction.PLACE_ONE
                || action == InventoryAction.PLACE_SOME
                || action == InventoryAction.SWAP_WITH_CURSOR
                || action == InventoryAction.HOTBAR_MOVE_AND_READD
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.COLLECT_TO_CURSOR
                || click == ClickType.NUMBER_KEY
                || click == ClickType.DOUBLE_CLICK
                || click == ClickType.SWAP_OFFHAND;
        if (!interesting) return;
        schedule(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ir.badgerquest.plugin.gui.QuestGui) return;
        schedule(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        // If our own quest GUI just closed, GuiListener handles that separately.
        if (event.getInventory().getHolder() instanceof ir.badgerquest.plugin.gui.QuestGui) return;
        // Flush any bossbar deferred while this inventory was open.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            plugin.bossBarManager().flushOnClose(player);
            // Also do a scan — items moved via shift-click while it was open should now show.
            plugin.bossBarManager().scan(player);
        }, 1L);
    }

}
