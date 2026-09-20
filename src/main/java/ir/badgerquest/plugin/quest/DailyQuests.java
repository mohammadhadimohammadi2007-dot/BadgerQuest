package ir.badgerquest.plugin.quest;

import java.util.List;
import java.util.UUID;

public final class DailyQuests {

    private final UUID playerId;
    /** The day-key (YYYYMMDD in configured zone) that these quests were generated for. */
    private final int dayKey;
    private final List<Quest> quests;

    public DailyQuests(UUID playerId, int dayKey, List<Quest> quests) {
        this.playerId = playerId;
        this.dayKey = dayKey;
        this.quests = quests;
    }

    public UUID playerId() { return playerId; }
    public int dayKey() { return dayKey; }
    public List<Quest> quests() { return quests; }

    public Quest get(int oneBasedIndex) {
        if (oneBasedIndex < 1 || oneBasedIndex > quests.size()) return null;
        return quests.get(oneBasedIndex - 1);
    }

    public int completedCount() {
        int c = 0;
        for (Quest q : quests) if (q.completed()) c++;
        return c;
    }

    public boolean allCompleted() {
        for (Quest q : quests) if (!q.completed()) return false;
        return true;
    }
}
