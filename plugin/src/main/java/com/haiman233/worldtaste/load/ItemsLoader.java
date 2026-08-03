package com.haiman233.worldtaste.load;

import com.haiman233.worldtaste.WT;
import com.haiman233.worldtaste.items.ScriptItemFactory;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * 加载物品类配置。items.yml（消耗品/食材）与 machines.yml（脚本驱动的可放置物品，多为作物）
 * 结构一致，统一经 {@link ScriptItemFactory} 按脚本分派物品子类。
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
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            try {
                if (!register(id, s)) {
                    skip++;
                    continue;
                }
                String script = s.getString("script");
                if (script != null) WT.itemScripts.put(id, script.trim());
                ok++;
            } catch (Exception e) {
                WT.log(file + " " + id + " 注册失败: " + e);
                skip++;
            }
        }
        WT.plugin.getLogger().info(file + ": 注册 " + ok + ", 跳过 " + skip);
    }

    /** 通用物品注册（items/foods/mob_drops/geo/machines 复用）。成功返回 true。 */
    static boolean register(String id, ConfigurationSection s) {
        ItemGroup g = WT.group(s.getString("item_group"));
        if (g == null) {
            WT.log(id + ": 物品组 " + s.getString("item_group") + " 缺失，跳过");
            return false;
        }
        ItemStack display = WT.preload.get(id.toUpperCase(java.util.Locale.ROOT));
        if (display == null) {
            WT.log(id + ": 无展示物品，跳过");
            return false;
        }
        SlimefunItemStack sfis = new SlimefunItemStack(id, display);
        RecipeType rt = RecipeTypes.resolve(s.getString("recipe_type", "NULL"));
        ItemStack[] recipe = Read.recipe(s.getConfigurationSection("recipe"), 9);
        String script = s.getString("script");
        SlimefunItem item = ScriptItemFactory.create(id, g, sfis, rt, recipe, script != null ? script.trim() : null);
        item.register(WT.plugin);
        return true;
    }
}
