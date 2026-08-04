package com.haiman233.worldtaste.behavior;

import com.haiman233.worldtaste.items.CropBlock;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/** 监听作物方块破坏：已成熟则掉落作物/种子（对齐原 wt_crop.js onBreak）。 */
public final class CropListener implements Listener {

    public static final CropListener INSTANCE = new CropListener();

    private CropListener() {}

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        SlimefunItem sf = BlockStorage.check(e.getBlock());
        if (sf instanceof CropBlock crop) {
            // 禁用原版掉落：getDrops() 为空时框架不会 setDropItems(false)，
            // 而作物方块已被 tick 转成 WHEAT 等原版材质，否则会额外掉原版作物/种子
            e.setDropItems(false);
            crop.onBreak(e.getBlock());
        }
    }
}
