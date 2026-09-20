package ir.badgerquest.plugin.config;

import org.bukkit.Material;

import java.util.List;

public record GuiItem(Material material, int customModelData, String name, List<String> lore) {
    public static final GuiItem EMPTY = new GuiItem(Material.AIR, -1, " ", List.of());
}
