package ir.badgerquest.plugin.util;

import ir.badgerquest.plugin.config.GuiItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public final class ItemBuilder {

    private ItemBuilder() {}

    public static ItemStack build(GuiItem def, Map<String, String> placeholders) {
        Material mat = def.material();
        if (mat == null) mat = Material.STONE;
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(Text.apply(def.name(), placeholders)));
            List<String> lore = Text.color(Text.apply(def.lore(), placeholders));
            meta.setLore(lore);
            if (def.customModelData() >= 0) meta.setCustomModelData(def.customModelData());
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack build(Material material, int cmd, String name, List<String> lore,
                                  Map<String, String> placeholders) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(Text.apply(name, placeholders)));
            meta.setLore(Text.color(Text.apply(lore, placeholders)));
            if (cmd >= 0) meta.setCustomModelData(cmd);
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
