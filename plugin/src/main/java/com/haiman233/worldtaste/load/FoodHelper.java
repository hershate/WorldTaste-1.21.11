package com.haiman233.worldtaste.load;

import com.haiman233.worldtaste.WT;
import java.lang.reflect.Method;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.FoodComponent;

/**
 * 给食物物品应用 Paper FoodComponent（nutrition/saturation/canAlwaysEat/eatSeconds）。
 * FoodComponent 无公开工厂，反射实例化 CraftFoodComponent；eatSeconds 通过反射调用（Paper 1.21.6+ 支持）。
 */
public final class FoodHelper {

    private FoodHelper() {}

    public static void apply(ItemStack stack, int nutrition, float saturation, boolean canAlwaysEat, float eatSeconds) {
        if (stack == null || nutrition <= 0) return;
        stack.editMeta(meta -> {
            try {
                Class<?> craft = Class.forName("org.bukkit.craftbukkit.inventory.components.CraftFoodComponent");
                FoodComponent food = (FoodComponent) craft.getDeclaredConstructor().newInstance();
                food.setNutrition(nutrition);
                food.setSaturation(saturation);
                food.setCanAlwaysEat(canAlwaysEat);
                if (eatSeconds > 0) {
                    try {
                        Method m = food.getClass().getMethod("setEatSeconds", float.class);
                        m.invoke(food, eatSeconds);
                    } catch (NoSuchMethodException ignored) {
                        // 当前 Paper 版本 FoodComponent 无 eatSeconds，忽略
                    }
                }
                meta.setFood(food);
            } catch (Throwable e) {
                WT.log("FoodComponent 应用失败: " + e.getMessage());
            }
        });
    }
}
