package com.haiman233.worldtaste.load;

import com.haiman233.worldtaste.WT;
import com.haiman233.worldtaste.machines.MenuDef;
import com.haiman233.worldtaste.machines.WTRecipe;
import com.haiman233.worldtaste.machines.WTRecipeMachine;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * 加载 template_machines.yml。模板机器原按“模板物品”分组配方；此处将所有模板下的配方拍平为一张配方表，
 * 以通用 {@link WTRecipeMachine} 注册（功能等价：均可合成，省略模板物品门槛）。模板门槛若需保留，后续单独实现。
 */
public final class TemplateLoader {

    private TemplateLoader() {}

    public static void load() {
        YamlConfiguration y = Yaml.loadResource(WT.plugin, "template_machines.yml");
        int ok = 0, skip = 0;
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            try {
                ItemGroup g = WT.group(s.getString("item_group"));
                if (g == null) { WT.log(id + ": 物品组缺失"); skip++; continue; }
                ItemStack display = WT.preload.get(id.toUpperCase(java.util.Locale.ROOT));
                if (display == null) { WT.log(id + ": 无展示物品"); skip++; continue; }
                SlimefunItemStack sfis = new SlimefunItemStack(id, display);
                RecipeType rt = RecipeTypes.resolve(s.getString("recipe_type", "NULL"));
                ItemStack[] craftRecipe = Read.recipe(s.getConfigurationSection("recipe"), 9);
                int[] input = RecipeMachineLoader.intList(s, "input");
                int[] output = RecipeMachineLoader.intList(s, "output");
                if (input.length == 0) input = new int[] { 10 };
                if (output.length == 0) output = new int[] { 16 };
                int capacity = s.getInt("capacity", 128);
                int consumption = s.getInt("consumption", s.getInt("energyPerCraft", 8));
                boolean hideAll = s.getBoolean("hideAllRecipes", false);
                List<WTRecipe> recipes = new ArrayList<>();
                ConfigurationSection recipesSec = s.getConfigurationSection("recipes");
                if (recipesSec != null) {
                    for (String tplId : recipesSec.getKeys(false)) {
                        ConfigurationSection tpl = recipesSec.getConfigurationSection(tplId);
                        if (tpl != null) recipes.addAll(RecipeMachineLoader.readRecipes(tpl, input.length));
                    }
                }
                MenuDef menu = WT.menus.get(id);
                WTRecipeMachine m = new WTRecipeMachine(g, sfis, rt, craftRecipe, input, output, recipes,
                        capacity, consumption, 1, menu, hideAll);
                m.register(WT.plugin);
                ok++;
            } catch (Exception e) {
                WT.log("template_machines.yml " + id + " 注册失败: " + e);
                skip++;
            }
        }
        WT.plugin.getLogger().info("template_machines.yml: 注册 " + ok + ", 跳过 " + skip);
    }
}
