package com.haiman233.worldtaste.behavior;

import com.haiman233.worldtaste.WT;
import com.haiman233.worldtaste.load.Yaml;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 行为数据注册表：加载生成器产出的 data/*.yml（consumables/crops/fishing），
 * 供 {@link com.haiman233.worldtaste.items.ScriptItemFactory} 在注册物品时查询脚本对应的行为。
 */
public final class Behaviors {

    private Behaviors() {}

    public static final Map<String, ConsumableOpts> consumables = new HashMap<>();
    public static final Map<String, CropCfg> crops = new HashMap<>();

    /** 读取数据文件（须在物品注册前调用）。 */
    public static void loadData() {
        loadConsumables();
        loadCrops();
        FishingListener.load();
    }

    /** 注册 Bukkit 事件监听器（须在所有物品注册后调用）。 */
    public static void registerListeners() {
        org.bukkit.Bukkit.getPluginManager().registerEvents(FishingListener.INSTANCE, WT.plugin);
        org.bukkit.Bukkit.getPluginManager().registerEvents(CropListener.INSTANCE, WT.plugin);
    }

    private static void loadConsumables() {
        YamlConfiguration y = Yaml.loadResource(WT.plugin, "data/consumables.yml");
        for (String name : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(name);
            if (s == null) continue;
            ConsumableOpts o = new ConsumableOpts();
            o.use = !"eat".equalsIgnoreCase(s.getString("kind", "use"));
            if (s.isSet("food")) o.food = s.getDouble("food");
            if (s.isSet("saturation")) o.saturation = s.getDouble("saturation");
            if (s.isSet("exhaustion")) o.exhaustion = s.getDouble("exhaustion");
            if (s.isSet("foodSet")) o.foodSet = s.getInt("foodSet");
            if (s.isSet("saturationSet")) o.saturationSet = (float) s.getDouble("saturationSet");
            o.requireHungry = s.getBoolean("requireHungry", false);
            if (s.isSet("satRegen")) o.satRegen = s.getInt("satRegen");
            if (s.isSet("unsatRegen")) o.unsatRegen = s.getInt("unsatRegen");
            if (s.isSet("starvation")) o.starvation = s.getInt("starvation");
            if (s.isSet("maxAir")) o.maxAir = s.getInt("maxAir");
            o.message = s.getString("message");
            consumables.put(name, o);
        }
        WT.plugin.getLogger().info("行为数据: consumables=" + consumables.size() + ", crops=" + 0);
    }

    private static void loadCrops() {
        YamlConfiguration y = Yaml.loadResource(WT.plugin, "data/crops.yml");
        for (String name : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(name);
            if (s == null) continue;
            CropCfg c = new CropCfg();
            Material m = Material.matchMaterial(s.getString("material", "WHEAT"));
            c.material = m != null ? m : Material.WHEAT;
            c.maxAge = s.getInt("maxAge", 7);
            c.growMs = s.getLong("growMs", 120000L);
            c.stages = s.getString("stages", "small");
            if (s.isList("drops")) {
                for (Map<?, ?> mm : s.getMapList("drops")) {
                    c.drops.add(new CropDrop((String) mm.get("id"), ((Number) mm.get("chance")).doubleValue(), 0));
                }
            } else if (s.isList("weightedDrops")) {
                for (Map<?, ?> mm : s.getMapList("weightedDrops")) {
                    c.drops.add(new CropDrop((String) mm.get("id"), 0, ((Number) mm.get("weight")).doubleValue()));
                }
                c.weighted = true;
            }
            crops.put(name, c);
        }
        WT.plugin.getLogger().info("行为数据: crops=" + crops.size());
    }

    /** 食物消耗参数（对应原 WT_eatConsumable opts）。 */
    public static final class ConsumableOpts {
        public boolean use = true;
        public Double food;
        public Double saturation;
        public Double exhaustion;
        public Integer foodSet;
        public Float saturationSet;
        public boolean requireHungry;
        public Integer satRegen;
        public Integer unsatRegen;
        public Integer starvation;
        public Integer maxAir;
        public String message;
    }

    /** 作物参数（对应原 WT_setupCrop cfg）。 */
    public static final class CropCfg {
        public Material material;
        public int maxAge;
        public long growMs;
        public String stages;
        public final List<CropDrop> drops = new ArrayList<>();
        public boolean weighted = false;
    }

    public static final class CropDrop {
        public final String id;
        public final double chance;
        public final double weight;
        public CropDrop(String id, double chance, double weight) {
            this.id = id; this.chance = chance; this.weight = weight;
        }
    }
}
