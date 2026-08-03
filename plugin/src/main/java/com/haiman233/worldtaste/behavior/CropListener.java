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
            crop.onBreak(e.getBlock());
        }
    }
}
