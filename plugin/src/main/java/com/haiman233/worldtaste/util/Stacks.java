package com.haiman233.worldtaste.util;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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

    /**
     * 安全扣除主手 1 个物品：数量减到 0 时清空槽位（set null），避免残留 0 数量“幽灵物品”。
     *
     * <p>直接 {@code setAmount(amount - 1)} 到 0 时，槽位仍持有一个 0 数量的原物品引用：
     * 其类型/Slimefun 绑定不变，会被 {@code SlimefunItem.getByItem} 或 {@code getType()} 继续识别为“存在”，
     * 从而被无消耗地重复利用（钓鱼鱼饵→无限钓获、打火石→无限点烟）。到 0 必须清空槽位。</p>
     */
    public static void consumeOneInMainHand(PlayerInventory inv) {
        if (inv == null) return;
        ItemStack it = inv.getItemInMainHand();
        if (it == null) return;
        int left = it.getAmount() - 1;
        if (left <= 0) inv.setItemInMainHand(null);
        else it.setAmount(left);
    }

    /** 同 {@link #consumeOneInMainHand}，作用于副手。 */
    public static void consumeOneInOffHand(PlayerInventory inv) {
        if (inv == null) return;
        ItemStack it = inv.getItemInOffHand();
        if (it == null) return;
        int left = it.getAmount() - 1;
        if (left <= 0) inv.setItemInOffHand(null);
        else it.setAmount(left);
    }
}
