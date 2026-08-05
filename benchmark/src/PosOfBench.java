// R2 专项微基准：量化 findMatch 内「输入槽 GUI 索引 → 数组位置」查表的前后开销。
//   旧（R2 前）：每 findMatch 调用都 new HashMap + N 次 put（Integer 装箱 + Node 分配）+ M 次 get。
//   新（R2）：构造期预算 int[] posBySlot（摊销为 0），findMatch 内仅做直接索引。
// 两种用例：
//   bound  —— 绑定槽机器（linked/workbench）：旧=build+lookup，新=lookup。
//   free   —— 自由槽机器（大多数 recipe_machines，bound=-1）：旧仍 build（从不 query，纯浪费），新=0。
package bench;

import java.util.HashMap;
import java.util.Map;

public final class PosOfBench {

    static final int[] GUI_SLOTS = {10, 11, 12, 13}; // 4 个输入槽（GUI 索引）
    static final int LOOKUPS = 4;                    // 绑定查询次数（bound 用例）

    // —— 旧路径：每调用一次重建 HashMap ——
    static long oldBound() {
        long acc = 0;
        Map<Integer, Integer> posOf = new HashMap<>();
        for (int s = 0; s < GUI_SLOTS.length; s++) posOf.put(GUI_SLOTS[s], s);
        for (int k = 0; k < LOOKUPS; k++) {
            Integer p = posOf.get(GUI_SLOTS[k]);
            if (p != null) acc += p;
        }
        return acc;
    }

    static long oldFree() {
        // 自由槽：bound 全为 -1，posOf 建了从不查 —— 但每 tick 仍付出 build 成本
        long acc = 0;
        Map<Integer, Integer> posOf = new HashMap<>();
        for (int s = 0; s < GUI_SLOTS.length; s++) posOf.put(GUI_SLOTS[s], s);
        acc += posOf.size();
        return acc;
    }

    // —— 新路径：构造期预算的 int[]（此处静态模拟），findMatch 内仅索引 ——
    static final int[] POS_BY_SLOT = buildPos();
    private static int[] buildPos() {
        int[] p = new int[54];
        java.util.Arrays.fill(p, -1);
        for (int s = 0; s < GUI_SLOTS.length; s++) p[GUI_SLOTS[s]] = s;
        return p;
    }

    static long newBound() {
        long acc = 0;
        for (int k = 0; k < LOOKUPS; k++) {
            int b = GUI_SLOTS[k];
            int p = (b >= 0 && b < POS_BY_SLOT.length) ? POS_BY_SLOT[b] : -1;
            if (p >= 0) acc += p;
        }
        return acc;
    }

    static long newFree() {
        // 自由槽：预算已摊销，findMatch 内对该查表零开销（返回常量防 DCE）
        return POS_BY_SLOT.length;
    }

    static long sink = 0;
}
