package com.haiman233.worldtaste.util;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** ItemStack 辅助操作。 */
public final class Stacks {

    private Stacks() {}

    /** 附魔发光（隐藏附魔纹）。 */
    public static void glow(ItemStack item) {
        if (item == null) return;
        ItemMeta m = item.getItemMeta();
        if (m == null) return;
        m.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(m);
    }
}
