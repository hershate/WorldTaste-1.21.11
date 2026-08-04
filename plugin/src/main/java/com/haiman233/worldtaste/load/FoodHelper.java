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

    /** 应用 FoodComponent。返回是否成功（反射路径失败时返回 false，便于上层统计升级告警）。 */
    public static boolean apply(ItemStack stack, int nutrition, float saturation, boolean canAlwaysEat, float eatSeconds) {
        if (stack == null || nutrition <= 0) return true;
        boolean[] ok = {true};
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
                ok[0] = false;
                WT.log("FoodComponent 应用失败: " + e.getMessage());
            }
        });
        return ok[0];
    }
}
