package com.haiman233.worldtaste.load;

import com.haiman233.worldtaste.WT;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/** 加载 recipe_types.yml 自定义配方类型，并提供 recipe_type 字符串解析。 */
public final class RecipeTypes {

    private RecipeTypes() {}

    public static void load() {
        YamlConfiguration y = Yaml.loadResource(WT.plugin, "recipe_types.yml");
        int ok = 0;
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            try {
                ItemStack display = Read.item(s, false);
                if (display == null) {
                    WT.log("recipe_types " + key + ": 无图标，跳过");
                    continue;
                }
                RecipeType rt = new RecipeType(new NamespacedKey(WT.plugin, key.toLowerCase(Locale.ROOT)), display);
                WT.recipeTypes.put(key.toUpperCase(Locale.ROOT), rt);
                ok++;
            } catch (Exception e) {
                WT.log("recipe_types " + key + " 失败: " + e);
            }
        }
        WT.plugin.getLogger().info("recipe_types.yml: 注册 " + ok);
    }

    /** 解析 recipe_type 字符串：先查自定义，再映射标准常量，最后回退 NULL。 */
    public static RecipeType resolve(String name) {
        if (name == null) return RecipeType.NULL;
        String u = name.toUpperCase(Locale.ROOT);
        if (u.equals("NULL") || u.isEmpty()) return RecipeType.NULL;
        RecipeType custom = WT.recipeTypes.get(u);
        if (custom != null) return custom;
        switch (u) {
            case "ENHANCED_CRAFTING_TABLE": return RecipeType.ENHANCED_CRAFTING_TABLE;
            case "MOB_DROP": return RecipeType.MOB_DROP;
            case "BARTER_DROP": return RecipeType.BARTER_DROP;
            case "MULTIBLOCK": return RecipeType.MULTIBLOCK;
            case "SMELTERY": return RecipeType.SMELTERY;
            case "GRIND_STONE": return RecipeType.GRIND_STONE;
            case "ORE_CRUSHER": return RecipeType.ORE_CRUSHER;
            case "COMPRESSOR": return RecipeType.COMPRESSOR;
            case "PRESSURE_CHAMBER": return RecipeType.PRESSURE_CHAMBER;
            case "MAGIC_WORKBENCH": return RecipeType.MAGIC_WORKBENCH;
            case "ORE_WASHER": return RecipeType.ORE_WASHER;
            case "JUICER": return RecipeType.JUICER;
            case "ANCIENT_ALTAR": return RecipeType.ANCIENT_ALTAR;
            case "INTERACT": return RecipeType.INTERACT;
            case "FOOD_FABRICATOR": return RecipeType.FOOD_FABRICATOR;
            case "FOOD_COMPOSTER": return RecipeType.FOOD_COMPOSTER;
            case "FREEZER": return RecipeType.FREEZER;
            case "REFINERY": return RecipeType.REFINERY;
            case "GEO_MINER": return RecipeType.GEO_MINER;
            default:
                return RecipeType.NULL;
        }
    }
}
