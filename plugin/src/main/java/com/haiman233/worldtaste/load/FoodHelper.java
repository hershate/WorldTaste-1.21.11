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

    /**
     * 应用 FoodComponent。返回是否成功（反射路径失败时返回 false，便于上层统计升级告警）。
     *
     * <p>{@code nutrition<=0} 时不再跳过：WorldTaste 多数饮品/汁（gz1/gz2/fmjpgz 等 {@code kind:eat}
     * 脚本）在 foods.yml 无 nutrition，其恢复值完全由 onEat 脚本（{@code opts.food/saturation}）提供。
     * 若不应用 FoodComponent 则物品不可食 → {@link com.haiman233.worldtaste.behavior.FoodConsumeListener}
     * 的 onEat 永不触发（~168 个饮品效果失效）。故 nutrition<=0 时给 0 营养 + canAlwaysEat=true，
     * 仅保证「可食」以触发 onEat，实际恢复交给脚本。</p>
     */
    public static boolean apply(ItemStack stack, int nutrition, float saturation, boolean canAlwaysEat, float eatSeconds) {
        if (stack == null) return true;
        final int food = nutrition > 0 ? nutrition : 0;
        final float sat = nutrition > 0 ? saturation : 0f;
        final boolean always = nutrition > 0 ? canAlwaysEat : true;
        boolean[] ok = {true};
        stack.editMeta(meta -> {
            try {
                Class<?> craft = Class.forName("org.bukkit.craftbukkit.inventory.components.CraftFoodComponent");
                FoodComponent fc = (FoodComponent) craft.getDeclaredConstructor().newInstance();
                fc.setNutrition(food);
                fc.setSaturation(sat);
                fc.setCanAlwaysEat(always);
                if (eatSeconds > 0) {
                    try {
                        Method m = fc.getClass().getMethod("setEatSeconds", float.class);
                        m.invoke(fc, eatSeconds);
                    } catch (NoSuchMethodException ignored) {
                        // 当前 Paper 版本 FoodComponent 无 eatSeconds，忽略
                    }
                }
                meta.setFood(fc);
            } catch (Throwable e) {
                ok[0] = false;
                WT.log("FoodComponent 应用失败: " + e.getMessage());
            }
        });
        return ok[0];
    }
}
