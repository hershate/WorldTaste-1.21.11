package com.haiman233.worldtaste.load;

import com.haiman233.worldtaste.WT;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 加载 mob_drops.yml：以普通物品注册（recipe_type 为 NULL），并记录 entity/chance，
 * 供 Phase 2 的生物死亡掉落监听器使用。
 */
public final class MobDropsLoader {

    private MobDropsLoader() {}

    public static final List<Drop> drops = new ArrayList<>();

    public static void load() {
        YamlConfiguration y = Yaml.loadResource(WT.plugin, "mob_drops.yml");
        int ok = 0, skip = 0;
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            try {
                // mob_drops 无 recipe_type/recipe：补充为 NULL 空配方以复用注册
                if (!s.isSet("recipe_type")) s.set("recipe_type", "NULL");
                if (!ItemsLoader.register(id, s)) {
                    skip++;
                    continue;
                }
                String entity = s.getString("entity");
                int chance = s.getInt("chance", 0);
                // 注册用的是 id_alias（effId），记录时也要用同一个 id，否则监听器 getById 查不到
                String effId = s.getString("id_alias", id);
                if (entity != null && chance > 0) drops.add(new Drop(effId, entity.toUpperCase(), chance));
                ok++;
            } catch (Exception e) {
                WT.log("mob_drops.yml " + id + " 注册失败: " + e);
                skip++;
            }
        }
        WT.plugin.getLogger().info("mob_drops.yml: 注册 " + ok + ", 跳过 " + skip);
    }

    public static final class Drop {
        public final String itemId;
        public final String entity;
        public final int chance;
        Drop(String itemId, String entity, int chance) {
            this.itemId = itemId;
            this.entity = entity;
            this.chance = chance;
        }
    }
}
