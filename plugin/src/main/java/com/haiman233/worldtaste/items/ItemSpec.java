package com.haiman233.worldtaste.items;

import org.bukkit.configuration.ConfigurationSection;

/** 从物品段解析出的属性集合（对齐 RSC ItemReader 读取的字段）。 */
public class ItemSpec {

    public String id;
    public String script;
    public boolean placeable = false; // RSC 默认 unplaceable
    public boolean soulbound;
    public boolean antiWither;
    public String radiation;       // Radioactivity 枚举名，null 表示无
    public Integer piglinChance;   // 0-100
    public Integer energyCapacity; // 电量容量
    public boolean vanilla;        // setUseableInWorkbench
    public String dropFrom;        // 方块材质名
    public Integer dropChance;     // 0-100
    public Integer dropAmount;     // 整数或最小值

    public static ItemSpec from(String id, ConfigurationSection s) {
        ItemSpec spec = new ItemSpec();
        spec.id = id;
        spec.script = s.getString("script");
        if (spec.script != null) spec.script = spec.script.trim();
        spec.placeable = s.getBoolean("placeable", false);
        spec.soulbound = s.getBoolean("soulbound", false);
        spec.antiWither = s.getBoolean("anti_wither", false);
        spec.radiation = s.getString("radiation");
        if (s.isSet("piglin_trade_chance")) spec.piglinChance = s.getInt("piglin_trade_chance", 100);
        if (s.isSet("energy_capacity")) spec.energyCapacity = s.getInt("energy_capacity", 0);
        spec.vanilla = s.getBoolean("vanilla", false);
        spec.dropFrom = s.getString("drop_from");
        spec.dropChance = s.getInt("drop_chance", 100);
        if (s.isSet("drop_amount")) spec.dropAmount = s.getInt("drop_amount", 1);
        return spec;
    }
}
