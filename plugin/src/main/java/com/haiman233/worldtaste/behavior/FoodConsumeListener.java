package com.haiman233.worldtaste.behavior;

import com.haiman233.worldtaste.behavior.Behaviors.ConsumableOpts;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;

/**
 * 食物进食事件：对 foods.yml 带 onEat 脚本(gz/rou 等)的食物，在进食后追加饥饿/饱和/消耗/药水等效果。
 * （食物的基础 nutrition 由 FoodComponent 提供，此处仅追加脚本效果，对齐原 WT_eatFood。）
 */
public final class FoodConsumeListener implements Listener {

    public static final FoodConsumeListener INSTANCE = new FoodConsumeListener();

    private FoodConsumeListener() {}

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        SlimefunItem sf = SlimefunItem.getByItem(e.getItem());
        if (sf == null) return;
        ConsumableOpts opts = Behaviors.foodOnEat.get(sf.getId());
        if (opts == null) return;
        Player p = e.getPlayer();
        if (opts.food != null) p.setFoodLevel(p.getFoodLevel() + opts.food.intValue());
        if (opts.saturation != null) p.setSaturation((float) (p.getSaturation() + opts.saturation));
        if (opts.exhaustion != null) p.setExhaustion((float) (p.getExhaustion() - opts.exhaustion));
        for (Behaviors.Potion pt : opts.potions) {
            org.bukkit.potion.PotionEffectType type = org.bukkit.potion.PotionEffectType.getByName(pt.type);
            if (type != null) p.addPotionEffect(new org.bukkit.potion.PotionEffect(type, pt.duration, pt.amplifier, false));
        }
    }
}
