package ir.badgerquest.plugin.db;

public final class PlayerRecord {
    public int streak;
    public int highscore;
    /** Day-key of last day the player completed ALL daily quests, or 0. */
    public int lastCompletionDay;
    /** Day-key of last day the player was seen active, for streak-reset absence check. */
    public int lastSeenDay;

    public PlayerRecord() {}

    public PlayerRecord(int streak, int highscore, int lastCompletionDay, int lastSeenDay) {
        this.streak = streak;
        this.highscore = highscore;
        this.lastCompletionDay = lastCompletionDay;
        this.lastSeenDay = lastSeenDay;
    }
}
