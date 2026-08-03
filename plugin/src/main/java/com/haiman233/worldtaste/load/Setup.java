package com.haiman233.worldtaste.load;

import com.haiman233.worldtaste.WT;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/** 内容加载编排：按依赖顺序注册 groups → recipe_types → 预加载展示物品 → 各内容文件。 */
public final class Setup {

    private Setup() {}

    /** 含有可被其它配方以 material_type:slimefun 引用的“物品”的文件（需预加载展示堆）。 */
    private static final String[] ITEM_FILES = {
        "items.yml", "foods.yml", "mob_drops.yml", "geo_resources.yml",
        "recipe_machines.yml", "mb_machines.yml", "linked_recipe_machines.yml",
        "template_machines.yml", "workbenches.yml"
    };

    public static void loadAll() {
        long t = System.currentTimeMillis();
        GroupLoader.load();
        RecipeTypes.load();
        preloadDisplays();
        com.haiman233.worldtaste.behavior.Behaviors.loadData();
        ItemsLoader.load();
        FoodsLoader.load();
        MobDropsLoader.load();
        MenuLoader.load();
        RecipeMachineLoader.load();
        MultiBlockLoader.load();
        TemplateLoader.load();
        GeoLoader.load();
        com.haiman233.worldtaste.behavior.Behaviors.registerListeners();
        WT.plugin.getLogger().info("基础内容加载完成，耗时 " + (System.currentTimeMillis() - t) + "ms");
    }

    /** 第一遍：把各物品/机器的展示堆加入 WT.preload，使后续配方解析能跨文件按 id 引用。 */
    private static void preloadDisplays() {
        for (String file : ITEM_FILES) {
            YamlConfiguration y = Yaml.loadResource(WT.plugin, file);
            for (String id : y.getKeys(false)) {
                ConfigurationSection s = y.getConfigurationSection(id);
                if (s == null) continue;
                ConfigurationSection itemSec = s.getConfigurationSection("item");
                if (itemSec == null) continue;
                ItemStack display = Read.item(itemSec, false);
                if (display != null) WT.preload.put(id.toUpperCase(java.util.Locale.ROOT), display);
            }
        }
    }
}
