package com.haiman233.worldtaste.behavior;

import com.haiman233.worldtaste.load.MobDropsLoader;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/** 生物死亡掉落（对齐原 mob_drops.yml 的 entity+chance）。 */
public final class MobDropListener implements Listener {

    public static final MobDropListener INSTANCE = new MobDropListener();

    private MobDropListener() {}

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        String type = e.getEntityType().name();
        for (MobDropsLoader.Drop d : MobDropsLoader.drops) {
            if (!d.entity.equals(type)) continue;
            if (ThreadLocalRandom.current().nextInt(100) < d.chance) {
                SlimefunItem sf = SlimefunItem.getById(d.itemId);
                if (sf != null) e.getDrops().add(sf.getItem().clone());
            }
        }
    }
}
