package ir.badgerquest.plugin.placeholder;

import ir.badgerquest.plugin.BadgerQuest;
import ir.badgerquest.plugin.db.PlayerRecord;
import ir.badgerquest.plugin.quest.DailyQuests;
import ir.badgerquest.plugin.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public final class BadgerPlaceholders extends PlaceholderExpansion {

    private final BadgerQuest plugin;

    public BadgerPlaceholders(BadgerQuest plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "badgerquest"; }
    @Override public @NotNull String getAuthor() { return "BadgerQuest"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        PlayerRecord rec = plugin.questManager().playerOf(player.getUniqueId());
        DailyQuests daily = plugin.questManager().dailyOf(player.getUniqueId());
        int streak = rec != null ? rec.streak : 0;
        int highscore = rec != null ? rec.highscore : 0;

        return switch (params.toLowerCase()) {
            case "streak" -> String.valueOf(streak);
            case "highscore" -> String.valueOf(highscore);
            case "completed_today" -> daily == null ? "0" : String.valueOf(daily.completedCount());
            case "quests_per_day" -> String.valueOf(plugin.config().questsPerDay());
            case "time_until_reset" -> Text.formatDuration(plugin.time().secondsUntilNextReset());
            case "days_until_streak_reset" -> {
                if (rec == null || rec.lastSeenDay == 0) yield String.valueOf(plugin.config().streakResetAfterDays());
                long absence = plugin.time().daysBetween(rec.lastSeenDay, plugin.time().currentDayKey());
                yield String.valueOf(Math.max(0, plugin.config().streakResetAfterDays() - absence));
            }
            default -> null;
        };
    }
}
