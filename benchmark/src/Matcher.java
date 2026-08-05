// 匹配算法的两个实现，均逐行对齐生产端 WTRecipeMachine.findMatch（含 anyInput 早返回、绑定/自由槽、
// distinct 去重、fitAll、stillValid 复校）。二者唯一差别：PrunedMatcher 在昂贵 similarity() 之前
// 插入「两端均 SF 且 id 不同 → 必不匹配」的廉价必要条件预筛，并每 tick 预解析一次各槽 SF id。
//
// 预筛的「安全性」（不改变匹配结果，仅跳过确定不匹配者）依据 isItemSimilar 源码：
// 当 in 与 need 均为已注册 SF 物品且 id 不同时，其 both-SF 分支 (SlimefunUtils.java:363-366) 必返回 false；
// 其余情形（任一为原版、或 id 相同）一律仍交 similarity() 定夺，完整保留 RSC 保真度。
package bench;

import java.util.List;

public final class Matcher {

    /** 生产端现状：线性扫描，每输入每比较都调用 similarity()（内含 2× getByItem）。 */
    static SimItem[] matchLinear(SimItem[] slots, List<Recipe> recipes) {
        int slotCount = slots.length;
        boolean anyInput = false;
        for (SimItem si : slots) if (si != null) { anyInput = true; break; }
        if (!anyInput) return null;

        for (Recipe recipe : recipes) {
            SimItem[] inputs = recipe.inputs;
            int n = inputs.length;
            int[] chosen = new int[n];
            int matched = 0;
            boolean failed = false;
            for (int i = 0; i < n; i++) {
                chosen[i] = -1;
                SimItem need = inputs[i];
                if (need == null) { matched++; continue; }
                int bound = recipe.inSlot(i);
                if (bound >= 0) {
                    if (bound >= slotCount) { failed = true; break; }
                    SimItem in = slots[bound];
                    if (in != null && in.amount >= need.amount && Cost.similarity(in, need)) {
                        chosen[i] = bound; matched++;
                    } else { failed = true; break; }
                } else {
                    for (int s = 0; s < slotCount; s++) {
                        SimItem in = slots[s];
                        if (in != null && in.amount >= need.amount && Cost.similarity(in, need)) {
                            chosen[i] = s; matched++; break;
                        }
                    }
                }
            }
            if (failed || matched != n) continue;
            int distinct = 0;
            for (int i = 0; i < n; i++) {
                boolean dup = false;
                for (int j = 0; j < i; j++) if (chosen[i] == chosen[j]) { dup = true; break; }
                if (!dup) distinct++;
            }
            if (distinct != n) continue;
            if (!fitAll()) continue;
            // stillValid 复校：对匹配输入再 similarity 一次（生产端对非 noConsume、chosen>=0 的输入）
            for (int i = 0; i < n; i++) {
                if (!recipe.isNoConsume(i) && chosen[i] >= 0) Cost.similarity(slots[chosen[i]], inputs[i]);
            }
            return inputs; // 命中
        }
        return null;
    }

    /** 优化版：每 tick 预解析各槽 SF id（S 次 resolve），随后用廉价 id 预筛跳过确定不匹配的 similarity()。 */
    static SimItem[] matchPruned(SimItem[] slots, List<Recipe> recipes) {
        int slotCount = slots.length;
        boolean anyInput = false;
        for (SimItem si : slots) if (si != null) { anyInput = true; break; }
        if (!anyInput) return null;

        // —— 本轮优化核心：每槽每 tick 仅解析一次 SF id（生产端 getItemData 读 PDC）——
        String[] slotSfId = new String[slotCount];
        for (int s = 0; s < slotCount; s++) slotSfId[s] = Cost.resolveSlotId(slots[s]);

        for (Recipe recipe : recipes) {
            SimItem[] inputs = recipe.inputs;
            int n = inputs.length;
            int[] chosen = new int[n];
            int matched = 0;
            boolean failed = false;
            for (int i = 0; i < n; i++) {
                chosen[i] = -1;
                SimItem need = inputs[i];
                if (need == null) { matched++; continue; }
                int bound = recipe.inSlot(i);
                String needId = recipe.needSfId(i);
                if (bound >= 0) {
                    if (bound >= slotCount) { failed = true; break; }
                    SimItem in = slots[bound];
                    if (in != null && in.amount >= need.amount
                            && !idCertainlyMismatch(slotSfId[bound], needId)
                            && Cost.similarity(in, need)) {
                        chosen[i] = bound; matched++;
                    } else { failed = true; break; }
                } else {
                    for (int s = 0; s < slotCount; s++) {
                        SimItem in = slots[s];
                        if (in != null && in.amount >= need.amount
                                && !idCertainlyMismatch(slotSfId[s], needId)
                                && Cost.similarity(in, need)) {
                            chosen[i] = s; matched++; break;
                        }
                    }
                }
            }
            if (failed || matched != n) continue;
            int distinct = 0;
            for (int i = 0; i < n; i++) {
                boolean dup = false;
                for (int j = 0; j < i; j++) if (chosen[i] == chosen[j]) { dup = true; break; }
                if (!dup) distinct++;
            }
            if (distinct != n) continue;
            if (!fitAll()) continue;
            for (int i = 0; i < n; i++) {
                if (!recipe.isNoConsume(i) && chosen[i] >= 0) Cost.similarity(slots[chosen[i]], inputs[i]);
            }
            return inputs;
        }
        return null;
    }

    /**
     * 生产端的「机器级闸门」：仅当机器为纯-SF（≥2 配方且所有非空输入均为 SF 物品）时才启用预筛；
     * 否则完全回退到 matchLinear（零回归）。纯-SF 机器的扫描代价由 getByItem 主导（预筛收益≫每 tick 解析开销）；
     * 原版/混合机器的代价由廉价类型短路主导（预筛无收益反增开销），故闸门关闭。
     */
    static SimItem[] matchGated(SimItem[] slots, List<Recipe> recipes, boolean gateOn) {
        return gateOn ? matchPruned(slots, recipes) : matchLinear(slots, recipes);
    }

    /** 两端均 SF 且 id 不同 → isItemSimilar 必为 false（both-SF 分支按 id 比较）。其余一律不跳过。 */
    private static boolean idCertainlyMismatch(String inId, String needId) {
        return inId != null && needId != null && !inId.equals(needId);
    }

    /** 生产端 InvUtils.fitAll（输出能否放下的判定）非本热路径优化对象，stub 为 true。 */
    private static boolean fitAll() { return true; }
}
