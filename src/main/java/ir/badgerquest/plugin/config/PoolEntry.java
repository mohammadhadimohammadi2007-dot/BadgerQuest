package ir.badgerquest.plugin.config;

import org.bukkit.Material;

public record PoolEntry(String id,
                        Material material,
                        int customModelData,
                        String displayName,
                        int min,
                        int max) {
}
