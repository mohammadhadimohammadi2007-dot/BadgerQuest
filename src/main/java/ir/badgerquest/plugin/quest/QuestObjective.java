package ir.badgerquest.plugin.quest;

import ir.badgerquest.plugin.config.PoolEntry;

public final class QuestObjective {

    private final String poolId;
    private final int required;
    private int collected;
    /** Cached PoolEntry — always the config resolution of poolId. */
    private final PoolEntry entry;

    public QuestObjective(PoolEntry entry, int required, int collected) {
        this.entry = entry;
        this.poolId = entry.id();
        this.required = required;
        this.collected = Math.min(collected, required);
    }

    public PoolEntry entry() { return entry; }
    public String poolId() { return poolId; }
    public int required() { return required; }
    public int collected() { return collected; }
    public int remaining() { return Math.max(0, required - collected); }
    public boolean isComplete() { return collected >= required; }

    /**
     * Adds up to `amount` to this objective, capped at required.
     * Returns how many were actually consumed (0..amount).
     */
    public int consume(int amount) {
        if (amount <= 0 || isComplete()) return 0;
        int room = required - collected;
        int used = Math.min(room, amount);
        collected += used;
        return used;
    }
}
