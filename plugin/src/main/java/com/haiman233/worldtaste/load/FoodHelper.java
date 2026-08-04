package com.haiman233.worldtaste.load;

import com.haiman233.worldtaste.WT;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.FoodComponent;

/**
 * 给食物物品应用 Paper FoodComponent（nutrition/saturation/canAlwaysEat）。
 * 对齐 RSC FoodReader.nbtApply：FoodComponent 无公开工厂，故反射实例化 CraftFoodComponent。
 */
public final class FoodHelper {

    private FoodHelper() {}

    public static void apply(ItemStack stack, int nutrition, float saturation, boolean canAlwaysEat) {
        if (stack == null || nutrition <= 0) return;
        stack.editMeta(meta -> {
            try {
                Class<?> craft = Class.forName("org.bukkit.craftbukkit.inventory.components.CraftFoodComponent");
                FoodComponent food = (FoodComponent) craft.getDeclaredConstructor().newInstance();
                food.setNutrition(nutrition);
                food.setSaturation(saturation);
                food.setCanAlwaysEat(canAlwaysEat);
                meta.setFood(food);
            } catch (Throwable e) {
                WT.log("FoodComponent 应用失败: " + e.getMessage());
            }
        });
    }
}
