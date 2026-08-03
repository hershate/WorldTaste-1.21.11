package com.haiman233.worldtaste.items;

import com.haiman233.worldtaste.behavior.Behaviors;
import com.haiman233.worldtaste.behavior.Behaviors.ConsumableOpts;
import com.haiman233.worldtaste.behavior.Behaviors.CropCfg;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.inventory.ItemStack;

/**
 * 按脚本名分派物品子类：作物 → {@link CropBlock}；标准消耗 → {@link ConsumableItem}；
 * 特殊脚本 → {@link SpecialItems}；其余 → 普通 {@link SlimefunItem}。
 */
public final class ScriptItemFactory {

    private ScriptItemFactory() {}

    public static SlimefunItem create(String id, ItemGroup group, SlimefunItemStack sfis,
                                      RecipeType rt, ItemStack[] recipe, String script) {
        if (script != null) {
            CropCfg crop = Behaviors.crops.get(script);
            if (crop != null) return new CropBlock(group, sfis, rt, recipe, crop);
            ConsumableOpts opts = Behaviors.consumables.get(script);
            if (opts != null && opts.use) return new ConsumableItem(group, sfis, rt, recipe, opts);
            SlimefunItem special = SpecialItems.create(id, group, sfis, rt, recipe, script);
            if (special != null) return special;
        }
        return new SlimefunItem(group, sfis, rt, recipe);
    }
}
