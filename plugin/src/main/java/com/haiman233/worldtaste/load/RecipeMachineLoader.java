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

/** 加载 recipe_machines.yml → {@link WTRecipeMachine}。 */
public final class RecipeMachineLoader {

    private RecipeMachineLoader() {}

    public static void load() {
        // 电力配方机器：recipe_machines / linked_recipe_machines（workbench 由 WorkbenchLoader 单独处理）
        load("recipe_machines.yml");
        load("linked_recipe_machines.yml");
    }

    public static void load(String file) {
        YamlConfiguration y = Yaml.loadResource(WT.plugin, file);
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
                int[] input = intList(s, "input");
                int[] output = intList(s, "output");
                if (input.length == 0) input = new int[] { 10 };
                if (output.length == 0) output = new int[] { 16 };
                int capacity = s.getInt("capacity", 128);
                int energyPerCraft = s.getInt("energyPerCraft", 8);
                int speed = s.getInt("speed", 1);
                boolean hideAll = s.getBoolean("hideAllRecipes", false);
                List<WTRecipe> recipes = readRecipes(s.getConfigurationSection("recipes"), input.length);
                MenuDef menu = WT.menus.get(id);
                WTRecipeMachine m = new WTRecipeMachine(g, sfis, rt, craftRecipe, input, output, recipes,
                        capacity, energyPerCraft, speed, menu, hideAll);
                m.register(WT.plugin);
                ok++;
            } catch (Exception e) {
                WT.log(file + " " + id + " 注册失败: " + e);
                skip++;
            }
        }
        WT.plugin.getLogger().info(file + ": 注册 " + ok + ", 跳过 " + skip);
    }

    /** 读取 recipe_machines 的 recipes 段。 */
    public static List<WTRecipe> readRecipes(ConfigurationSection recipesSec, int defaultInputSize) {
        List<WTRecipe> out = new ArrayList<>();
        if (recipesSec == null) return out;
        for (String name : recipesSec.getKeys(false)) {
            ConfigurationSection r = recipesSec.getConfigurationSection(name);
            if (r == null) continue;
            try {
                int seconds = r.getInt("seconds", 1);
                ConfigurationSection inSec = r.getConfigurationSection("input");
                int nIn = inSec == null ? 0 : Math.max(1, inSec.getKeys(false).size());
                ItemStack[] input = Read.recipe(inSec, Math.max(nIn, defaultInputSize));
                input = compact(input);
                boolean[] noConsume = new boolean[input.length];
                boolean noConsumeAll = r.getBoolean("noConsume", false);
                if (inSec != null) {
                    for (int i = 0; i < input.length; i++) {
                        ConfigurationSection is = inSec.getConfigurationSection(String.valueOf(i + 1));
                        noConsume[i] = noConsumeAll || (is != null && is.getBoolean("noConsume", false));
                    }
                }
                List<ItemStack> outs = new ArrayList<>();
                List<Integer> chances = new ArrayList<>();
                ConfigurationSection outSec = r.getConfigurationSection("output");
                if (outSec != null) {
                    for (String k : outSec.getKeys(false)) {
                        ConfigurationSection o = outSec.getConfigurationSection(k);
                        if (o == null) continue;
                        ItemStack it = Read.item(o, true);
                        if (it == null) continue;
                        outs.add(it);
                        chances.add(o.getInt("chance", 100));
                    }
                }
                boolean chooseOne = r.getBoolean("chooseOne", false);
                int[] ch = chances.stream().mapToInt(Integer::intValue).toArray();
                out.add(new WTRecipe(seconds, input, outs.toArray(new ItemStack[0]), ch, chooseOne, noConsume));
            } catch (Exception e) {
                WT.log("配方 " + name + " 解析失败: " + e);
            }
        }
        return out;
    }

    public static int[] intList(ConfigurationSection s, String key) {
        List<Integer> list = s.getIntegerList(key);
        int[] out = new int[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i);
        return out;
    }

    private static ItemStack[] compact(ItemStack[] arr) {
        int n = 0;
        for (ItemStack i : arr) if (i != null) n++;
        ItemStack[] out = new ItemStack[n];
        int idx = 0;
        for (ItemStack i : arr) if (i != null) out[idx++] = i;
        return out;
    }
}
