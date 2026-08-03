package com.haiman233.worldtaste.load;

import com.haiman233.worldtaste.WT;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * 加载 items.yml。本阶段先以普通 {@link SlimefunItem} 注册（保证内容全部入服）；
 * 脚本名记录到 {@link WT#itemScripts}，后续由行为层挂接。
 */
public final class ItemsLoader {

    private ItemsLoader() {}

    public static void load() {
        YamlConfiguration y = Yaml.loadResource(WT.plugin, "items.yml");
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
                WT.log("items.yml " + id + " 注册失败: " + e);
                skip++;
            }
        }
        WT.plugin.getLogger().info("items.yml: 注册 " + ok + ", 跳过 " + skip);
    }

    /** 通用物品注册（items.yml 与 foods.yml/machines.yml 共用）。成功返回 true。 */
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
        new SlimefunItem(g, sfis, rt, recipe).register(WT.plugin);
        return true;
    }
}
