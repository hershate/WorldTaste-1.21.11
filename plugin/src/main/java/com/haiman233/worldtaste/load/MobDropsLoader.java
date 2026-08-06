package com.haiman233.worldtaste.load;

import com.haiman233.worldtaste.WT;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 加载 mob_drops.yml：以普通物品注册（recipe_type 为 NULL），并记录 entity/chance，
 * 供 Phase 2 的生物死亡掉落监听器使用。
 */
public final class MobDropsLoader {

    private MobDropsLoader() {}

    /** 按实体类型(大写)索引的掉落表：onDeath 直接按类型查表，避免线性扫描全部掉落(刷怪塔高频死亡场景)。 */
    public static final Map<String, List<Drop>> drops = new HashMap<>();

    public static void load() {
        YamlConfiguration y = Yaml.loadResource(WT.plugin, "mob_drops.yml");
        int ok = 0, skip = 0;
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            try {
                // mob_drops 无 recipe_type/recipe：ItemsLoader.register 的 recipe_type 默认即为 "NULL"
                // （RecipeTypes.resolve("NULL")→RecipeType.NULL），故无需补充。
                // 不得在此向【共享】配置写入：R6 文件名缓存后 Yaml.loadResource 返回共享实例，
                // set 会污染缓存、违背「全部 Loader 只读」不变量（曾为此遗漏，r46 移除该冗余 set）。
                if (!ItemsLoader.register(id, s)) {
                    skip++;
                    continue;
                }
                String entity = s.getString("entity");
                int chance = s.getInt("chance", 0);
                // 注册用的是 id_alias（effId），记录时也要用同一个 id，否则监听器 getById 查不到
                String effId = s.getString("id_alias", id);
                if (entity != null && chance > 0) {
                    String key = entity.toUpperCase(Locale.ROOT);
                    drops.computeIfAbsent(key, k -> new ArrayList<>()).add(new Drop(effId, key, chance));
                }
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
