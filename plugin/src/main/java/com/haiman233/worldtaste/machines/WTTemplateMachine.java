package com.haiman233.worldtaste.machines;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.inventory.ItemStack;

/**
 * 模板机器（template_machines.yml）：在 templateSlot 放入对应模板物品后，仅匹配该模板下的配方。
 * 不放模板则不合成；模板物品不被消耗。
 */
public class WTTemplateMachine extends WTRecipeMachine {

    private final int templateSlot;
    private final Map<String, List<WTRecipe>> byTemplate;

    public WTTemplateMachine(ItemGroup group, SlimefunItemStack item, RecipeType rt, ItemStack[] recipe,
                             int[] input, int[] output, List<WTRecipe> allRecipes,
                             Map<String, List<WTRecipe>> byTemplate,
                             int capacity, int consumption, int speed, MenuDef menu, boolean hideAll, int templateSlot) {
        super(group, item, rt, recipe, input, output, allRecipes, capacity, consumption, speed, menu, hideAll);
        this.templateSlot = templateSlot;
        this.byTemplate = byTemplate;
    }

    @Override
    protected Set<Integer> extraFunctionalSlots() {
        return Collections.singleton(templateSlot);
    }

    @Override
    protected MachineRecipe findNextRecipe(BlockMenu inv) {
        ItemStack tpl = inv.getItemInSlot(templateSlot);
        SlimefunItem sf = SlimefunItem.getByItem(tpl);
        if (sf == null) return null;
        List<WTRecipe> list = byTemplate.get(sf.getId().toUpperCase(java.util.Locale.ROOT));
        if (list == null || list.isEmpty()) return null;
        return matchRecipes(inv, list);
    }
}
