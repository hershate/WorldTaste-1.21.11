package com.haiman233.worldtaste.items;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.inventory.ItemStack;

// Special script item dispatch (standalone .js scripts: jiu/yan/tang_*/yl_*/zhongdu_*/gandi/yurenjie...).
// Placeholder for now: unimplemented scripts return null and fall back to a plain SlimefunItem.
public final class SpecialItems {

    private SpecialItems() {}

    public static SlimefunItem create(String id, ItemGroup group, SlimefunItemStack sfis,
                                      RecipeType rt, ItemStack[] recipe, String script) {
        return null;
    }
}
