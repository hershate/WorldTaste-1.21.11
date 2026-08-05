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
     * <p>对齐 RSC {@code FoodReader}（[FoodReader.java:76-109](../../../../../../../../../../REF/RykenSlimeCustomizer-1.21.11/src/main/java/org/lins/mmmjjkx/rykenslimefuncustomizer/objects/yaml/item/FoodReader.java)）：
     * <ul>
     *   <li>{@code nutrition<1} → 提升为 1（WorldTaste 多数饮品/汁 gz1/gz2/fmjpgz 等 {@code kind:eat} 脚本
     *       在 foods.yml 无 nutrition，缺省 0；RSC 将 &lt;1 一律提升为 1 使其可食，恢复值主体由 onEat
     *       脚本 {@code opts.food/saturation} 提供）；</li>
     *   <li>{@code saturation<0} → 0；</li>
     *   <li>{@code canAlwaysEat} 取食物自身 {@code always_eatable}（默认 false，即饥饿时才可食）。</li>
     * </ul>
     * 反射类名 {@code org.bukkit.craftbukkit.inventory.components.CraftFoodComponent} 与 RSC 一致，
     * 经 RSC（Paper 1.21.11 规范实现）证实有效。注：此实现 supersede r17 的「nutrition=0+canAlwaysEat=true」方案——
     * 那虽达成可食但偏离 RSC 且引入「0 营养是否可食」不确定性；现营养恒 ≥1，消除该不确定性。</p>
     */
    public static boolean apply(ItemStack stack, int nutrition, float saturation, boolean canAlwaysEat, float eatSeconds) {
        if (stack == null) return true;
        if (nutrition < 1) nutrition = 1;
        if (saturation < 0f) saturation = 0f;
        final int fFood = nutrition;
        final float fSat = saturation;
        final boolean fAlways = canAlwaysEat;
        boolean[] ok = {true};
        stack.editMeta(meta -> {
            try {
                Class<?> craft = Class.forName("org.bukkit.craftbukkit.inventory.components.CraftFoodComponent");
                FoodComponent fc = (FoodComponent) craft.getDeclaredConstructor().newInstance();
                fc.setNutrition(fFood);
                fc.setSaturation(fSat);
                fc.setCanAlwaysEat(fAlways);
                if (eatSeconds > 0) {
                    try {
                        Method m = fc.getClass().getMethod("setEatSeconds", float.class);
                        m.invoke(fc, eatSeconds);
                    } catch (NoSuchMethodException ignored) {
                        // 当前 Paper 版本 FoodComponent 无 eatSeconds，忽略（与 RSC 一致）
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
