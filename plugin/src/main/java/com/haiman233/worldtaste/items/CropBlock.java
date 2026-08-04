package com.haiman233.worldtaste.items;

import com.haiman233.worldtaste.behavior.Behaviors.CropCfg;
import com.haiman233.worldtaste.behavior.Behaviors.CropDrop;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;

/**
 * 作物方块（machines.yml 中 script 为 seed/* 的物品）。对齐原脚本 WT_setupCrop：
 * 按 growMs 与生长阶段推进 Ageable 年龄；成熟后破坏掉落作物/种子。
 */
public class CropBlock extends SlimefunItem {

    /** 与 wt_crop.js 一致的小生长阶段。 */
    private static final double[] SMALL_STEPS = {1/10d, 1/6d, 1/3d, 0.5, 2/3d, 5/6d, 1d, 7/6d};

    private final CropCfg cfg;
    private final Map<Location, Long> lastUse = new ConcurrentHashMap<>();
    private final Set<Location> grown = ConcurrentHashMap.newKeySet();

    public CropBlock(ItemGroup group, SlimefunItemStack item, RecipeType rt, ItemStack[] recipe, CropCfg cfg) {
        super(group, item, rt, recipe);
        this.cfg = cfg;
    }

    /** 不由 Slimefun 框架掉落种子本身（仅由 CropListener 在成熟时掉落作物/种子，对齐 wt_crop.js）。 */
    @Override
    public List<ItemStack> getDrops() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void preRegister() {
        super.preRegister();
        addItemHandler(new BlockTicker() {
            @Override
            public boolean isSynchronized() { return true; }
            @Override
            public void tick(Block b, SlimefunItem item, Config data) {
                CropBlock.this.tick(b);
            }
        });
    }

    private void tick(Block b) {
        Location l = b.getLocation();
        Material type = b.getType();
        boolean isSeedHead = (type == Material.PLAYER_HEAD || type == Material.PLAYER_WALL_HEAD);
        if (type != cfg.material && !isSeedHead) {
            // 作物已被非玩家方式移除/替换（耕地破坏、爆炸、踩踏等）：
            // 清理状态并注销，避免幽灵 tick 把 AIR 设回作物刷原版种子
            lastUse.remove(l);
            grown.remove(l);
            me.mrCookieSlime.Slimefun.api.BlockStorage.clearBlockInfo(b);
            return;
        }
        if (isSeedHead) {
            // 新放置/重放的种子：清除可能残留的成熟标记（防同位置秒熟），重新开始生长
            grown.remove(l);
            lastUse.put(l, System.currentTimeMillis());
            setStage(b, 0);
            return;
        }
        if (grown.contains(l)) return;
        long now = System.currentTimeMillis();
        Long last = lastUse.get(l);
        if (last == null) {
            lastUse.put(l, now);
            setStage(b, 0);
            return;
        }
        long elapsed = now - last;
        for (int i = 0; i < SMALL_STEPS.length; i++) {
            if (elapsed < cfg.growMs * SMALL_STEPS[i]) {
                if (i > 0) setStage(b, (int) Math.floor(cfg.maxAge * ((double) i / SMALL_STEPS.length)));
                return;
            }
        }
        setStage(b, cfg.maxAge);
        grown.add(l);
    }

    private void setStage(Block b, int age) {
        if (b.getType() != cfg.material) b.setType(cfg.material);
        BlockState st = b.getState();
        if (st.getBlockData() instanceof Ageable a) {
            int target = Math.min(age, a.getMaximumAge());
            if (a.getAge() != target) {
                a.setAge(target);
                st.setBlockData(a);
                st.update(true);
            }
        }
    }

    /** 破坏时调用：若已成熟则掉落作物/种子。返回是否处理过。 */
    public boolean onBreak(Block b) {
        Location l = b.getLocation();
        boolean wasGrown = grown.remove(l);
        lastUse.remove(l);
        if (!wasGrown) return false;
        List<CropDrop> drops = cfg.drops;
        java.util.Random rnd = new java.util.Random();
        if (cfg.weighted && !drops.isEmpty()) {
            double total = 0;
            for (CropDrop d : drops) total += d.weight;
            double r = rnd.nextDouble() * total;
            for (CropDrop d : drops) {
                r -= d.weight;
                if (r <= 0) { dropItem(b, d.id); break; }
            }
        } else {
            for (CropDrop d : drops) {
                if (rnd.nextDouble() < d.chance) dropItem(b, d.id);
            }
        }
        return true;
    }

    private void dropItem(Block b, String id) {
        SlimefunItem sf = SlimefunItem.getById(id);
        ItemStack stack;
        if (sf != null) stack = sf.getItem();
        else {
            Material m = Material.matchMaterial(id);
            if (m == null) {
                com.haiman233.worldtaste.WT.log("作物 " + getId() + " 的掉落物无法解析: " + id);
                return;
            }
            stack = new ItemStack(m);
        }
        b.getWorld().dropItemNaturally(b.getLocation(), stack.clone());
    }
}
