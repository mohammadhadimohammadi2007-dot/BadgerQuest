package ir.badgerquest.plugin.listener;

import ir.badgerquest.plugin.BadgerQuest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerConnectionListener implements Listener {

    private final BadgerQuest plugin;

    public PlayerConnectionListener(BadgerQuest plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.questManager().ensureLoaded(event.getPlayer().getUniqueId(),
                () -> plugin.questManager().touchSeen(event.getPlayer().getUniqueId()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.bossBarManager().remove(event.getPlayer());
        plugin.questManager().unloadPlayer(event.getPlayer().getUniqueId());
    }
}
