package com.haiman233.worldtaste.load;

import com.haiman233.worldtaste.WT;
import com.haiman233.worldtaste.behavior.Behaviors;
import com.haiman233.worldtaste.behavior.Behaviors.ConsumableOpts;
import com.haiman233.worldtaste.items.ItemSpec;
import com.haiman233.worldtaste.items.ScriptItemFactory;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * 加载 foods.yml：应用 FoodComponent(nutrition/saturation/canAlwaysEat) 使食物可食，
 * 并登记 onEat 脚本(kind=eat)供 {@link com.haiman233.worldtaste.behavior.FoodConsumeListener} 追加效果。
 * eatseconds：Paper 1.21.11 的 FoodComponent 不支持，忽略（与 RSC 一致）。
 */
public final class FoodsLoader {

    private FoodsLoader() {}

    public static void load() {
        YamlConfiguration y = Yaml.loadResource(WT.plugin, "foods.yml");
        int ok = 0, skip = 0;
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            try {
                if (!RegisterConditions.pass(s)) { skip++; continue; }
                String effId = s.getString("id_alias", id);
                ItemGroup g = WT.group(s.getString("item_group"));
                if (g == null) { WT.log(effId + ": 物品组缺失"); skip++; continue; }
                ItemStack display = WT.preload.get(effId.toUpperCase(java.util.Locale.ROOT));
                if (display == null) { WT.log(effId + ": 无展示物品"); skip++; continue; }
                display = display.clone();
                FoodHelper.apply(display, s.getInt("nutrition", 0), (float) s.getDouble("saturation", 0), s.getBoolean("always_eatable", false));

                SlimefunItemStack sfis = new SlimefunItemStack(effId, display);
                RecipeType rt = RecipeTypes.resolve(s.getString("recipe_type", "NULL"));
                ItemStack[] recipe = Read.recipe(s.getConfigurationSection("recipe"), 9);
                ItemSpec spec = ItemSpec.from(effId, s);
                SlimefunItem item = ScriptItemFactory.create(spec, g, sfis, rt, recipe);
                if (spec.vanilla) { try { item.setUseableInWorkbench(true); } catch (Throwable ignored) {} }
                item.register(WT.plugin);

                String script = s.getString("script");
                if (script != null) {
                    ConsumableOpts opts = Behaviors.consumables.get(script.trim());
                    if (opts != null && !opts.use) Behaviors.foodOnEat.put(effId, opts);
                }
                ok++;
            } catch (Exception e) {
                WT.log("foods.yml " + id + " 注册失败: " + e);
                skip++;
            }
        }
        WT.plugin.getLogger().info("foods.yml: 注册 " + ok + ", 跳过 " + skip);
    }
}
