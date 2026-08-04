package com.haiman233.worldtaste.items;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.inventory.ItemStack;

/** 可放置的普通物品（对应 RSC placeable:true 的 CustomDefaultItem）。 */
public class WTItem extends SlimefunItem {
    public WTItem(ItemGroup group, SlimefunItemStack item, RecipeType rt, ItemStack[] recipe) {
        super(group, item, rt, recipe);
    }
}
