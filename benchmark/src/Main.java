// 微基准主程序：对每个场景 × {Linear, Pruned} × {noMatch, matchLast} 跑预热 + 计时，
// 输出每次匹配尝试的耗时(ns)、similarity 调用数、resolve 调用数，并算出加速比。
//
// 方法论（诚实声明）：
//   - 本基准为「算法微基准」，无 Bukkit/服务端依赖；similarity() 按 REF SlimefunUtils.isItemSimilar
//     的代价结构建模（类型校验廉价、类型匹配后 2× getByItem 昂贵）。
//   - 主指标「similarity 调用数/次」直接对应生产端 isItemSimilar 调用数（同一算法），可外推生产收益；
//     耗时为次指标，用于展示该减少在加权代价下的放大效果。
//   - 绝对生产 TPS 仍需真实服务端实测（与本仓库既有「无服务端」限制一致）。
package bench;

import java.util.List;

public final class Main {

    public static void main(String[] args) {
        System.out.println("WT recipe-match microbenchmark");
        System.out.println("warmup...");
        // 预热：各路径跑足量次，触发 JIT
        List<Recipe> warm = Scenarios.singleInputRecipes(31, true);
        for (int i = 0; i < 20_000; i++) {
            Matcher.matchLinear(Scenarios.noMatchSlots(1, true), warm);
            Matcher.matchPruned(Scenarios.noMatchSlots(1, true), warm);
        }
        System.out.println("warmup done\n");

        StringBuilder sb = new StringBuilder();
        header(sb);

        // 真实规模场景
        run(sb, "SHUIZUXIANG 78 单输入·原版材质", Scenarios.singleInputRecipes(78, false), 1, false);
        run(sb, "HWSCPCLJ 31 单输入·SF头颅", Scenarios.singleInputRecipes(31, true), 1, true);
        run(sb, "FUHUASHI 22 双输入·混合", Scenarios.dualInputRecipes(22), 2, true);

        // 压力放大：把 SF 头颅配方数拉到 200，看线性 vs 预筛的渐近差距
        run(sb, "STRESS 200 单输入·SF头颅", Scenarios.singleInputRecipes(200, true), 1, true);

        System.out.print(sb);

        System.out.println("\n(busywork sink=" + Cost.sink + " —— 非零证明代价未被死码消除)");
    }

    private static void header(StringBuilder sb) {
        sb.append(String.format("%-34s %-4s | %-8s %-4s | %-18s | %-22s | %s%n",
                "scenario", "gate", "case", "in#",
                "Linear(cur): ns sim",
                "Optimized: ns sim res",
                "speedup"));
        sb.append("-".repeat(120)).append('\n');
    }

    private static void run(StringBuilder sb, String name, List<Recipe> recipes, int slotCount, boolean sf) {
        SimItem[] noMatch = Scenarios.noMatchSlots(slotCount, sf);
        SimItem[] matchLast = Scenarios.matchLastSlots(recipes);
        boolean gate = pureSfGate(recipes);

        Result linNo = measure(recipes, noMatch, false);
        Result linHit = measure(recipes, matchLast, false);
        String gateTag = gate ? "ON " : "off";

        if (gate) {
            Result optNo = measure(recipes, noMatch, true);
            Result optHit = measure(recipes, matchLast, true);
            sb.append(String.format("%-30s g=%s | %8s d=%d | L:ns=%6d sim=%4d | O:ns=%6d sim=%4d res=%d | %.2fx%n",
                    name, gateTag, "noMatch", slotCount,
                    linNo.nsPerOp, linNo.simPerOp,
                    optNo.nsPerOp, optNo.simPerOp, optNo.resPerOp,
                    ratio(linNo.nsPerOp, optNo.nsPerOp)));
            sb.append(String.format("%-30s g=%s | %8s d=%d | L:ns=%6d sim=%4d | O:ns=%6d sim=%4d res=%d | %.2fx%n",
                    name, gateTag, "hitLast", slotCount,
                    linHit.nsPerOp, linHit.simPerOp,
                    optHit.nsPerOp, optHit.simPerOp, optHit.resPerOp,
                    ratio(linHit.nsPerOp, optHit.nsPerOp)));
        } else {
            // 闸门关闭：生产端 Optimized 与 Linear 代码路径完全相同（matchGated 直接回退 matchLinear），
            // 故加速比按构造恒为 1.00（零回归），不再单独测量以避免把 JIT 噪声误报为「加速/劣化」。
            sb.append(String.format("%-30s g=%s | %8s d=%d | ns=%6d sim=%4d | %-20s | %.2fx%n",
                    name, gateTag, "noMatch", slotCount,
                    linNo.nsPerOp, linNo.simPerOp,
                    "= Linear (gate off)", 1.00));
            sb.append(String.format("%-30s g=%s | %8s d=%d | ns=%6d sim=%4d | %-20s | %.2fx%n",
                    name, gateTag, "hitLast", slotCount,
                    linHit.nsPerOp, linHit.simPerOp,
                    "= Linear (gate off)", 1.00));
        }
    }

    /** 生产端闸门：≥2 配方 且 所有非空输入均为 SF 物品（needSfId!=null）时开启预筛。 */
    private static boolean pureSfGate(List<Recipe> recipes) {
        if (recipes.size() < 2) return false;
        for (Recipe r : recipes) {
            for (int i = 0; i < r.inputs.length; i++) {
                if (r.inputs[i] != null && r.needSfId(i) == null) return false;
            }
        }
        return true;
    }

    private static double ratio(long lin, long pru) {
        return pru == 0 ? 0 : (double) lin / pru;
    }

    private static Result measure(List<Recipe> recipes, SimItem[] slots, boolean optimized) {
        int iters = 2_000;
        long t0 = System.nanoTime();
        for (int i = 0; i < iters; i++) {
            Matcher.matchGated(slots, recipes, optimized);
        }
        long perOp = (System.nanoTime() - t0) / iters;
        if (perOp > 0) iters = Math.max(2_000, (int) (300_000_000L / perOp));
        Cost.resetCounters();
        t0 = System.nanoTime();
        for (int i = 0; i < iters; i++) {
            Matcher.matchGated(slots, recipes, optimized);
        }
        long ns = System.nanoTime() - t0;
        Result r = new Result();
        r.nsPerOp = ns / iters;
        r.simPerOp = Cost.similarityCalls / iters;
        r.resPerOp = Cost.resolveCalls / iters;
        return r;
    }

    static final class Result {
        long nsPerOp, simPerOp, resPerOp;
    }
}
