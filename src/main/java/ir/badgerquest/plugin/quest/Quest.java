package ir.badgerquest.plugin.quest;

import java.util.List;

public final class Quest {

    /** 1-based index (1..questsPerDay). */
    private final int index;
    private final List<QuestObjective> objectives;
    private boolean accepted;
    private boolean completed;
    /** Epoch millis when accepted, or 0. */
    private long acceptedAt;

    public Quest(int index, List<QuestObjective> objectives, boolean accepted, boolean completed, long acceptedAt) {
        this.index = index;
        this.objectives = objectives;
        this.accepted = accepted;
        this.completed = completed;
        this.acceptedAt = acceptedAt;
    }

    public int index() { return index; }
    public List<QuestObjective> objectives() { return objectives; }
    public boolean accepted() { return accepted; }
    public boolean completed() { return completed; }
    public long acceptedAt() { return acceptedAt; }

    public void accept(long nowMillis) {
        if (accepted || completed) return;
        accepted = true;
        acceptedAt = nowMillis;
    }

    public void markCompleted() { completed = true; }

    public boolean allObjectivesComplete() {
        for (QuestObjective o : objectives) if (!o.isComplete()) return false;
        return true;
    }
}
