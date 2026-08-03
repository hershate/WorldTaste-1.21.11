package com.haiman233.worldtaste.load;

import com.haiman233.worldtaste.WT;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 加载 geo_resources.yml。当前以普通物品注册（recipe_type 为 GEO_MINER 展示）。
 * 注意：未注册为真正的 GEOResource，故 GEO 采掘机暂不会产出；后续如需可在本处接入 GEOResource 注册。
 */
public final class GeoLoader {

    private GeoLoader() {}

    public static void load() {
        YamlConfiguration y = Yaml.loadResource(WT.plugin, "geo_resources.yml");
        int ok = 0;
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            try {
                if (ItemsLoader.register(id, s)) ok++;
            } catch (Exception e) {
                WT.log("geo_resources.yml " + id + " 注册失败: " + e);
            }
        }
        WT.plugin.getLogger().info("geo_resources.yml: 注册 " + ok);
    }
}
