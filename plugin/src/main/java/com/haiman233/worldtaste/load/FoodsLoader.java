package com.haiman233.worldtaste.load;

import com.haiman233.worldtaste.WT;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 加载 foods.yml。本阶段复用普通物品注册；nutrition/saturation 与 onEat 脚本
 * 留待行为层（Phase 2）通过 FoodComponent / 事件处理实现。脚本名记入 {@link WT#itemScripts}。
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
                if (!ItemsLoader.register(id, s)) {
                    skip++;
                    continue;
                }
                String script = s.getString("script");
                if (script != null) WT.itemScripts.put(id, script.trim());
                ok++;
            } catch (Exception e) {
                WT.log("foods.yml " + id + " 注册失败: " + e);
                skip++;
            }
        }
        WT.plugin.getLogger().info("foods.yml: 注册 " + ok + ", 跳过 " + skip);
    }
}
