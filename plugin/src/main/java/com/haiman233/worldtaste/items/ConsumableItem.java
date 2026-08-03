package com.haiman233.worldtaste.items;

import com.haiman233.worldtaste.behavior.Behaviors.ConsumableOpts;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.NotPlaceable;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * 消耗型食物（右键食用）。对齐原脚本 WT_eatConsumable：
 * 主手消耗 1、副手禁持粘液物品、按 opts 恢复饥饿/饱和/消耗/再生速率/氧气等。
 */
public class ConsumableItem extends SimpleSlimefunItem<ItemUseHandler> implements NotPlaceable {

    private final ConsumableOpts opts;

    public ConsumableItem(ItemGroup group, SlimefunItemStack item, RecipeType rt, ItemStack[] recipe, ConsumableOpts opts) {
        super(group, item, rt, recipe);
        this.opts = opts;
    }

    @Override
    public ItemUseHandler getItemHandler() {
        return e -> {
            Player p = e.getPlayer();
            if (opts.requireHungry && p.getFoodLevel() >= 20) return;
            PlayerInventory inv = p.getInventory();
            ItemStack off = inv.getItemInOffHand();
            if (off != null && SlimefunItem.getByItem(off) != null) {
                p.sendMessage("您必须使用主手进食且副手不能持有粘液科技物品！");
                return;
            }
            ItemStack main = inv.getItemInMainHand();
            if (main == null || main.getAmount() <= 0) return;
            main.setAmount(main.getAmount() - 1);

            if (opts.foodSet != null) p.setFoodLevel(opts.foodSet);
            else if (opts.food != null) p.setFoodLevel(p.getFoodLevel() + opts.food.intValue());
            if (opts.saturationSet != null) p.setSaturation(opts.saturationSet);
            else if (opts.saturation != null) p.setSaturation((float) (p.getSaturation() + opts.saturation));
            if (opts.exhaustion != null) p.setExhaustion((float) (p.getExhaustion() - opts.exhaustion));
            if (opts.satRegen != null) p.setSaturatedRegenRate(opts.satRegen);
            if (opts.unsatRegen != null) p.setUnsaturatedRegenRate(opts.unsatRegen);
            if (opts.starvation != null) p.setStarvationRate(opts.starvation);
            if (opts.maxAir != null) p.setMaximumAir(opts.maxAir);

            if (opts.message != null) p.sendMessage(opts.message);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_STRIDER_EAT, 1f, 1f);
        };
    }
}
