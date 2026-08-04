package com.haiman233.worldtaste.load;

import com.haiman233.worldtaste.WT;
import com.haiman233.worldtaste.behavior.BlockDrops;
import com.haiman233.worldtaste.items.ItemSpec;
import com.haiman233.worldtaste.items.ScriptItemFactory;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * 加载物品类配置。items.yml（消耗品/食材/装饰）与 machines.yml（脚本驱动的可放置物品，多为作物）结构一致，
 * 统一经 {@link ScriptItemFactory} 按脚本+属性分派物品子类。支持 register.conditions、lateInit(两遍)、id_alias、
 * placeable、drop_from、vanilla、radiation/soulbound/anti_wither/piglin/energy 等属性。
 */
public final class ItemsLoader {

    private ItemsLoader() {}

    public static void load() {
        loadFile("items.yml");
        loadFile("machines.yml");
    }

    public static void loadFile(String file) {
        YamlConfiguration y = Yaml.loadResource(WT.plugin, file);
        int ok = 0, skip = 0;
        List<ConfigurationSection> late = new ArrayList<>();
        List<String> lateIds = new ArrayList<>();
        // 第一遍：非 lateInit
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            if (s.getBoolean("lateInit", false)) {
                late.add(s);
                lateIds.add(id);
                continue;
            }
            try {
                if (register(id, s)) ok++;
                else skip++;
            } catch (Exception e) {
                WT.log(file + " " + id + " 注册失败: " + e);
                skip++;
            }
        }
        // 第二遍：lateInit
        for (int i = 0; i < late.size(); i++) {
            String id = lateIds.get(i);
            try {
                if (register(id, late.get(i))) ok++;
                else skip++;
            } catch (Exception e) {
                WT.log(file + " " + id + "(lateInit) 注册失败: " + e);
                skip++;
            }
        }
        WT.plugin.getLogger().info(file + ": 注册 " + ok + ", 跳过 " + skip);
    }

    /** 通用物品注册（items/machines/foods/mob_drops/geo 复用）。成功返回 true。 */
    static boolean register(String id, ConfigurationSection s) {
        // 注册条件
        if (!RegisterConditions.pass(s)) return false;

        String effId = s.getString("id_alias", id);
        ItemGroup g = WT.group(s.getString("item_group"));
        if (g == null) {
            WT.log(effId + ": 物品组 " + s.getString("item_group") + " 缺失，跳过");
            return false;
        }
        ItemStack display = WT.preload.get(effId.toUpperCase(java.util.Locale.ROOT));
        if (display == null) display = WT.preload.get(id.toUpperCase(java.util.Locale.ROOT));
        if (display == null) {
            WT.log(effId + ": 无展示物品，跳过");
            return false;
        }
        SlimefunItemStack sfis = new SlimefunItemStack(effId, display);
        RecipeType rt = RecipeTypes.resolve(s.getString("recipe_type", "NULL"));
        ItemStack[] recipe = Read.recipe(s.getConfigurationSection("recipe"), 9);

        ItemSpec spec = ItemSpec.from(effId, s);
        SlimefunItem item = ScriptItemFactory.create(spec, g, sfis, rt, recipe);

        if (spec.vanilla) {
            try { item.setUseableInWorkbench(true); } catch (Throwable ignored) {}
        }
        item.register(WT.plugin);

        // 方块破坏掉落
        if (spec.dropFrom != null) {
            Material block = Material.matchMaterial(spec.dropFrom);
            int amount = parseAmount(s.getString("drop_amount", "1"));
            BlockDrops.add(block, effId, spec.dropChance, amount);
        }

        String script = s.getString("script");
        if (script != null) WT.itemScripts.put(effId, script.trim());
        return true;
    }

    private static int parseAmount(String value) {
        if (value == null) return 1;
        int dash = value.indexOf('-');
        if (dash > 0) {
            try {
                int lo = Integer.parseInt(value.substring(0, dash).trim());
                int hi = Integer.parseInt(value.substring(dash + 1).trim());
                return ThreadLocalRandom.current().nextInt(lo, hi + 1);
            } catch (NumberFormatException ignored) { }
        }
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException e) { return 1; }
    }
}
