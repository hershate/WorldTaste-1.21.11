package com.haiman233.worldtaste.behavior;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.util.ArrayList;
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

    public static void add(Material block, String itemId, int chance, int amount) {
        if (block == null) return;
        MAP.computeIfAbsent(block, k -> new ArrayList<>()).add(new Drop(itemId, chance, amount));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        List<Drop> drops = MAP.get(e.getBlock().getType());
        if (drops == null) return;
        for (Drop d : drops) {
            if (ThreadLocalRandom.current().nextInt(100) >= d.chance) continue;
            SlimefunItem sf = SlimefunItem.getById(d.itemId);
            if (sf == null) continue;
            ItemStack stack = sf.getItem().clone();
            stack.setAmount(Math.max(1, d.amount));
            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), stack);
        }
    }

    private static final class Drop {
        final String itemId;
        final int chance;
        final int amount;
        Drop(String itemId, int chance, int amount) {
            this.itemId = itemId; this.chance = chance; this.amount = amount;
        }
    }
}
