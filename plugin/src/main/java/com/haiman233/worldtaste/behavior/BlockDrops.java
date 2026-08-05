package com.haiman233.worldtaste.behavior;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.util.ArrayList;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 方块破坏掉落（drop_from/drop_chance/drop_amount）。某些物品由破坏指定方块按概率掉落。
 */
public final class BlockDrops implements Listener {

    public static final BlockDrops INSTANCE = new BlockDrops();

    private static final Map<Material, List<Drop>> MAP = new HashMap<>();

    private BlockDrops() {}

    public static void add(Material block, String itemId, int chance, int minAmount, int maxAmount) {
        if (block == null) return;
        MAP.computeIfAbsent(block, k -> new ArrayList<>()).add(new Drop(itemId, chance, minAmount, maxAmount));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        List<Drop> drops = MAP.get(e.getBlock().getType());
        if (drops == null) return;
        // drop_from 语义为「破坏自然方块的附赠掉落」。已注册的粘液方块（含作物 CropBlock）由各自的
        // 掉落逻辑（Slimefun BlockBreakHandler / CropListener）负责，此处必须跳过，否则双重掉落：
        // 例如成熟为 SWEET_BERRY_BUSH 的 WorldTaste 作物被破坏时，既掉作物产物，
        // 又掉 items.yml 中 drop_from:SWEET_BERRY_BUSH 的附赠物（WT_NGSCZZ1/2，8%/7%）。
        // 同理防止任何材质命中 drop_from 的已放置粘液方块被重复掉落（放置→破坏即可刷的复制）。
        if (BlockStorage.check(e.getBlock()) != null) return;
        for (Drop d : drops) {
            if (ThreadLocalRandom.current().nextInt(100) >= d.chance) continue;
            SlimefunItem sf = SlimefunItem.getById(d.itemId);
            if (sf == null) continue;
            ItemStack stack = sf.getItem().clone();
            // 数量区间在每次掉落时掷（而非加载期一次定值）
            int amount = (d.maxAmount > d.minAmount)
                    ? ThreadLocalRandom.current().nextInt(d.minAmount, d.maxAmount + 1)
                    : d.minAmount;
            stack.setAmount(Math.max(1, amount));
            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), stack);
        }
    }

    private static final class Drop {
        final String itemId;
        final int chance;
        final int minAmount;
        final int maxAmount;
        Drop(String itemId, int chance, int minAmount, int maxAmount) {
            this.itemId = itemId; this.chance = chance;
            this.minAmount = minAmount; this.maxAmount = maxAmount;
        }
    }
}
