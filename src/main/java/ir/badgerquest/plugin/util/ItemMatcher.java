package ir.badgerquest.plugin.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemMatcher {

    private ItemMatcher() {}

    /**
     * Matches material AND custom_model_data.
     * If required CMD is -1 the CMD check is skipped (matches any CMD, including absent).
     */
    public static boolean matches(ItemStack stack, Material requiredMaterial, int requiredCmd) {
        if (stack == null || stack.getType() != requiredMaterial) return false;
        if (requiredCmd < 0) return true;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return requiredCmd == 0;
        int actual = meta.hasCustomModelData() ? meta.getCustomModelData() : 0;
        return actual == requiredCmd;
    }
}
