package ir.badgerquest.plugin.quest;

import ir.badgerquest.plugin.config.ConfigManager;
import ir.badgerquest.plugin.config.PoolEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class QuestGenerator {

    private final ConfigManager cfg;

    public QuestGenerator(ConfigManager cfg) {
        this.cfg = cfg;
    }

    public DailyQuests generate(UUID playerId, int dayKey) {
        List<PoolEntry> pool = cfg.pool();
        int questsPerDay = cfg.questsPerDay();
        int itemsPerQuest = cfg.itemsPerQuest();

        List<Quest> out = new ArrayList<>(questsPerDay);
        // seed off player+day so a given player+day always regenerates the same quest deterministically
        // (useful if load races with generation; also gives per-player, per-day uniqueness)
        Random rng = new Random(playerId.getMostSignificantBits() ^ playerId.getLeastSignificantBits() ^ ((long) dayKey * 2654435761L));

        for (int qi = 1; qi <= questsPerDay; qi++) {
            List<QuestObjective> objs = new ArrayList<>(itemsPerQuest);
            List<PoolEntry> shuffled = new ArrayList<>(pool);
            Collections.shuffle(shuffled, rng);
            for (int i = 0; i < itemsPerQuest; i++) {
                PoolEntry pe;
                if (i < shuffled.size()) {
                    pe = shuffled.get(i);
                } else {
                    pe = shuffled.get(rng.nextInt(shuffled.size()));
                }
                int req = pe.min() + (pe.max() > pe.min() ? rng.nextInt(pe.max() - pe.min() + 1) : 0);
                objs.add(new QuestObjective(pe, req, 0));
            }
            out.add(new Quest(qi, objs, false, false, 0L));
        }
        return new DailyQuests(playerId, dayKey, out);
    }

    // Unused but kept for callers who want a fully random (non-deterministic) generation
    public DailyQuests generateRandom(UUID playerId, int dayKey) {
        List<PoolEntry> pool = cfg.pool();
        int questsPerDay = cfg.questsPerDay();
        int itemsPerQuest = cfg.itemsPerQuest();
        List<Quest> out = new ArrayList<>(questsPerDay);
        for (int qi = 1; qi <= questsPerDay; qi++) {
            List<QuestObjective> objs = new ArrayList<>(itemsPerQuest);
            List<PoolEntry> shuffled = new ArrayList<>(pool);
            Collections.shuffle(shuffled, ThreadLocalRandom.current());
            for (int i = 0; i < itemsPerQuest; i++) {
                PoolEntry pe = i < shuffled.size() ? shuffled.get(i) : shuffled.get(ThreadLocalRandom.current().nextInt(shuffled.size()));
                int req = pe.min() + (pe.max() > pe.min() ? ThreadLocalRandom.current().nextInt(pe.max() - pe.min() + 1) : 0);
                objs.add(new QuestObjective(pe, req, 0));
            }
            out.add(new Quest(qi, objs, false, false, 0L));
        }
        return new DailyQuests(playerId, dayKey, out);
    }
}
