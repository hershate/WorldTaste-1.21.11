package com.haiman233.worldtaste.items;

import io.github.thebusybiscuit.slimefun4.api.geo.GEOResource;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.inventory.ItemStack;

/** GEO 资源（geo_resources.yml）：注册后可被 GEO 采掘机产出、GEO 扫描仪探测。 */
public class WTGeoResource implements GEOResource {

    private final NamespacedKey key;
    private final ItemStack item;
    private final String name;
    private final int maxDeviation;
    private final boolean obtainable;
    private final int supplyNormal;
    private final int supplyNether;
    private final int supplyEnd;

    public WTGeoResource(NamespacedKey key, ItemStack item, String name, int maxDeviation, boolean obtainable,
                         int supplyNormal, int supplyNether, int supplyEnd) {
        this.key = key;
        this.item = item;
        this.name = name;
        this.maxDeviation = maxDeviation;
        this.obtainable = obtainable;
        this.supplyNormal = supplyNormal;
        this.supplyNether = supplyNether;
        this.supplyEnd = supplyEnd;
    }

    @Override
    public NamespacedKey getKey() { return key; }

    @Override
    public int getDefaultSupply(Environment env, Biome biome) {
        switch (env) {
            case NETHER: return supplyNether;
            case THE_END: return supplyEnd;
            default: return supplyNormal;
        }
    }

    @Override
    public int getMaxDeviation() { return maxDeviation; }

    @Override
    public String getName() { return name; }

    @Override
    public ItemStack getItem() { return item; }

    @Override
    public boolean isObtainableFromGEOMiner() { return obtainable; }
}
