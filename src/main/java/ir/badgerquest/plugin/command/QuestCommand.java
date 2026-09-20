package ir.badgerquest.plugin.command;

import ir.badgerquest.plugin.BadgerQuest;
import ir.badgerquest.plugin.gui.QuestGui;
import ir.badgerquest.plugin.quest.DailyQuests;
import ir.badgerquest.plugin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QuestCommand implements CommandExecutor, TabCompleter {

    private final BadgerQuest plugin;

    public QuestCommand(BadgerQuest plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1) {
            String sub = args[0].toLowerCase();
            if (sub.equals("reload")) {
                if (!sender.hasPermission("badgerquest.admin")) {
                    sender.sendMessage(msg("no_permission"));
                    return true;
                }
                plugin.reloadAll();
                sender.sendMessage(msg("reload_success"));
                return true;
            }
            if (sub.equals("reset") && args.length >= 2) {
                if (!sender.hasPermission("badgerquest.admin")) {
                    sender.sendMessage(msg("no_permission"));
                    return true;
                }
                String name = args[1];
                Player target = Bukkit.getPlayerExact(name);
                UUID id;
                if (target != null) id = target.getUniqueId();
                else id = Bukkit.getOfflinePlayer(name).getUniqueId();
                plugin.questManager().unloadPlayer(id);
                plugin.database().deletePlayer(id);
                sender.sendMessage(Text.color(plugin.config().prefix()
                        + Text.apply(plugin.config().message("reset_success"), Map.of("player", name))));
                return true;
            }
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("not_a_player"));
            return true;
        }
        if (!player.hasPermission("badgerquest.use")) {
            player.sendMessage(msg("no_permission"));
            return true;
        }
        openMenu(player);
        return true;
    }

    private void openMenu(Player player) {
        plugin.questManager().ensureLoaded(player.getUniqueId(), () -> {
            DailyQuests daily = plugin.questManager().dailyOf(player.getUniqueId());
            if (daily == null) {
                player.sendMessage(msg("no_permission")); // shouldn't happen
                return;
            }
            QuestGui.open(plugin, player);
        });
    }

    private String msg(String key) {
        return Text.color(plugin.config().prefix() + plugin.config().message(key));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("badgerquest.admin")) {
                if ("reload".startsWith(args[0].toLowerCase())) out.add("reload");
                if ("reset".startsWith(args[0].toLowerCase())) out.add("reset");
            }
        } else if (args.length == 2 && "reset".equalsIgnoreCase(args[0]) && sender.hasPermission("badgerquest.admin")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
            }
        }
        return out;
    }
}
