// 数据生成器：依据真实 recipe_machines.yml 的配方形态构造代表性场景。
//   SHUIZUXIANG : 78 配方，单输入，原版材质（各异）—— 对应 isItemSimilar 廉价路径（类型不符即 false）。
//   HWSCPCLJ    : 31 配方，单输入，SF 头颅（同材质 PLAYER_HEAD，各异 SF id）—— 对应昂贵路径（每次比较 2× getByItem）。
//   FUHUASHI    : 22 配方，双输入（原版+SF 混合），自由槽 —— 多输入 + distinct 复核。
package bench;

import java.util.ArrayList;
import java.util.List;

public final class Scenarios {

    static final int PLAYER_HEAD = 1; // SF 头颅材质占位

    /** 单输入场景：nRecipes 个配方，sf=true 时全为同材质 SF 头颅（昂贵），否则各异原版材质（廉价）。 */
    static List<Recipe> singleInputRecipes(int nRecipes, boolean sf) {
        List<Recipe> out = new ArrayList<>(nRecipes);
        for (int k = 0; k < nRecipes; k++) {
            SimItem in = sf
                    ? new SimItem(PLAYER_HEAD, "SF_HEAD_" + k, 1)
                    : new SimItem(100 + k, null, 1); // 原版材质各异
            SimItem[] inputs = {in};
            out.add(new Recipe(inputs, new int[]{-1}, new boolean[]{false}));
        }
        return out;
    }

    /** 双输入场景：[原版各异, SF 头颅各异]，自由槽。 */
    static List<Recipe> dualInputRecipes(int nRecipes) {
        List<Recipe> out = new ArrayList<>(nRecipes);
        for (int k = 0; k < nRecipes; k++) {
            SimItem a = new SimItem(100 + k, null, 1);              // 原版
            SimItem b = new SimItem(PLAYER_HEAD, "SF_HEAD_" + k, 1);// SF 头颅
            out.add(new Recipe(new SimItem[]{a, b}, new int[]{-1, -1}, new boolean[]{false, false}));
        }
        return out;
    }

    /** 构造一个「与所有配方都不匹配」的输入（输出阻塞/错误输入的高负载最坏情形，每 tick 全扫描）。 */
    static SimItem[] noMatchSlots(int slotCount, boolean sf) {
        SimItem[] slots = new SimItem[slotCount];
        for (int s = 0; s < slotCount; s++) {
            slots[s] = sf
                    ? new SimItem(PLAYER_HEAD, "SF_HEAD_NONE_" + s, 1)
                    : new SimItem(9000 + s, null, 1);
        }
        return slots;
    }

    /** 构造一个「匹配最后一条配方」的输入（首 N-1 条全扫描后命中）。 */
    static SimItem[] matchLastSlots(List<Recipe> recipes) {
        Recipe last = recipes.get(recipes.size() - 1);
        int slotCount = last.inputs.length;
        SimItem[] slots = new SimItem[slotCount];
        for (int i = 0; i < slotCount; i++) {
            SimItem in = last.inputs[i];
            slots[i] = in == null ? null : new SimItem(in.type, in.sfId, in.amount);
        }
        return slots;
    }
}
