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
    /** foods.yml 带 onEat 脚本(kind=eat)的食物：itemId -> opts */
    public static final Map<String, ConsumableOpts> foodOnEat = new HashMap<>();

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
        org.bukkit.Bukkit.getPluginManager().registerEvents(MobDropListener.INSTANCE, WT.plugin);
        org.bukkit.Bukkit.getPluginManager().registerEvents(BlockDrops.INSTANCE, WT.plugin);
        org.bukkit.Bukkit.getPluginManager().registerEvents(FoodConsumeListener.INSTANCE, WT.plugin);
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
            if (s.isSet("exhaustionSet")) o.exhaustionSet = s.getDouble("exhaustionSet");
            if (s.isSet("absorption")) o.absorption = s.getDouble("absorption");
            o.gameMode = s.getString("gameMode");
            if (s.isSet("remainingAirAdd")) o.remainingAirAdd = s.getInt("remainingAirAdd");
            if (s.isSet("foodSet")) o.foodSet = s.getInt("foodSet");
            if (s.isSet("saturationSet")) o.saturationSet = (float) s.getDouble("saturationSet");
            o.requireHungry = s.getBoolean("requireHungry", false);
            if (s.isSet("satRegen")) o.satRegen = s.getInt("satRegen");
            if (s.isSet("unsatRegen")) o.unsatRegen = s.getInt("unsatRegen");
            if (s.isSet("starvation")) o.starvation = s.getInt("starvation");
            if (s.isSet("maxAir")) o.maxAir = s.getInt("maxAir");
            if (s.isSet("remainingAir")) o.remainingAir = s.getInt("remainingAir");
            if (s.isSet("freezeTicks")) o.freezeTicks = s.getInt("freezeTicks");
            if (s.isSet("randomFood")) o.randomFood = s.getInt("randomFood");
            String offTool = s.getString("offhandTool");
            if (offTool != null && !offTool.isEmpty()) {
                o.offhandTool = Material.matchMaterial(offTool.trim().toUpperCase(java.util.Locale.ROOT));
            }
            o.consumeOffhand = s.getBoolean("consumeOffhand", false);
            if (s.isList("potions")) {
                for (Map<?, ?> pm : s.getMapList("potions")) {
                    Object t = pm.get("type");
                    Object d = pm.get("duration");
                    Object a = pm.get("amplifier");
                    if (t != null && d instanceof Number && a instanceof Number) {
                        o.potions.add(new Potion(t.toString(), ((Number) d).intValue(), ((Number) a).intValue()));
                    }
                }
            }
            o.message = s.getString("message");
            consumables.put(name, o);
        }
        WT.plugin.getLogger().info("行为数据: consumables=" + consumables.size());
    }

    private static void loadCrops() {
        YamlConfiguration y = Yaml.loadResource(WT.plugin, "data/crops.yml");
        int skip = 0;
        for (String name : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(name);
            if (s == null) continue;
            try {
                CropCfg c = new CropCfg();
                Material m = Material.matchMaterial(s.getString("material", "WHEAT"));
                c.material = m != null ? m : Material.WHEAT;
                c.maxAge = s.getInt("maxAge", 7);
                c.growMs = s.getLong("growMs", 120000L);
                c.stages = s.getString("stages", "small");
                if (s.isList("drops")) {
                    for (Map<?, ?> mm : s.getMapList("drops")) {
                        // 显式校验类型：缺 chance 或非数值曾导致 NPE/CCE 逃出 loadData、
                        // 连累其后的全部加载(items/foods/machines…)被 onEnable 顶层 catch 跳过。
                        Object id = mm.get("id");
                        Object ch = mm.get("chance");
                        if (id instanceof String && ch instanceof Number) {
                            c.drops.add(new CropDrop((String) id, ((Number) ch).doubleValue(), 0));
                        } else {
                            WT.log("crop " + name + " 的 drops 项缺少 id/chance，跳过该项");
                        }
                    }
                } else if (s.isList("weightedDrops")) {
                    for (Map<?, ?> mm : s.getMapList("weightedDrops")) {
                        Object id = mm.get("id");
                        Object w = mm.get("weight");
                        if (id instanceof String && w instanceof Number) {
                            double wv = ((Number) w).doubleValue();
                            c.drops.add(new CropDrop((String) id, 0, wv));
                            c.weightTotal += wv; // R8: load 期预算权重总和（对齐 R4）
                        } else {
                            WT.log("crop " + name + " 的 weightedDrops 项缺少 id/weight，跳过该项");
                        }
                    }
                    c.weighted = true;
                }
                crops.put(name, c);
            } catch (Exception e) {
                WT.log("crop " + name + " 解析失败，跳过: " + e);
                skip++;
            }
        }
        WT.plugin.getLogger().info("行为数据: crops=" + crops.size() + (skip > 0 ? ", 跳过 " + skip : ""));
    }

    /** 食物消耗参数（对应原 WT_eatConsumable opts，并扩展覆盖独立脚本的空气/冻结/药水等）。 */
    public static final class ConsumableOpts {
        public boolean use = true;
        public Double food;
        public Double saturation;
        public Double exhaustion;
        public Double exhaustionSet;
        public Double absorption;
        public String gameMode;
        public Integer remainingAirAdd;
        public Integer foodSet;
        public Float saturationSet;
        public boolean requireHungry;
        public Integer satRegen;
        public Integer unsatRegen;
        public Integer starvation;
        public Integer maxAir;
        public Integer remainingAir;
        public Integer freezeTicks;
        public Integer randomFood;
        public Material offhandTool;
        public boolean consumeOffhand;
        public final List<Potion> potions = new ArrayList<>();
        public String message;
    }

    public static final class Potion {
        public final String type;
        public final int duration;
        public final int amplifier;
        public Potion(String type, int duration, int amplifier) {
            this.type = type; this.duration = duration; this.amplifier = amplifier;
        }
    }

    /** 作物参数（对应原 WT_setupCrop cfg）。 */
    public static final class CropCfg {
        public Material material;
        public int maxAge;
        public long growMs;
        public String stages;
        public final List<CropDrop> drops = new ArrayList<>();
        public boolean weighted = false;
        /** 加权掉落的权重总和，load 期一次预算（对齐 R4 FishingListener.Bait.total）。
         *  CropBlock.onBreak 直接用此值，消除每次收获对 drops 的求和（O(n)→O(1)）。仅 weighted 作物有意义。 */
        public double weightTotal = 0;
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
