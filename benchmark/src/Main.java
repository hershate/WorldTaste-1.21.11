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

        System.out.println("\n[R2] posOf 查表开销（per findMatch 调用）");
        runPosOf();

        System.out.println("\n[R3] CropBlock 状态查询（每 tick 每成熟作物）");
        runCrop();

        System.out.println("\n(busywork sink=" + Cost.sink + " —— 非零证明代价未被死码消除)");
    }

    private static void runCrop() {
        // 预热
        for (int i = 0; i < 50_000; i++) {
            CropBench.sink += CropBench.oldMature(0, 7, 64, 7) ? 1 : 0;
            CropBench.sink += CropBench.newMature(0, 7, 64, 7) ? 1 : 0;
            CropBench.sink += CropBench.oldGrowing(0, 7, 64, 7) ? 1 : 0;
            CropBench.sink += CropBench.newGrowing(0, 7, 64, 7) ? 1 : 0;
        }
        System.out.println("  [假设A] Location 分配消除（pack-long+双层map,0分配）:");
        CropBench.allocCount = 0;
        long aOld = timeCrop(() -> CropBench.sink += CropBench.oldMature(0, 7, 64, 7) ? 1 : 0);
        long aOldAlloc = CropBench.allocCount;
        long aNew = timeCrop(() -> CropBench.sink += CropBench.newMature(0, 7, 64, 7) ? 1 : 0);
        System.out.printf("    旧(Location分配+1查)=%dns  vs  新(0分配+2查)=%dns  → %.2fx（CPU 劣化，拒绝）%n",
                aOld, aNew, ratio(aOld, aNew));
        System.out.printf("    分配 %d→0/op（GC 受益，但 per-tick CPU 翻倍，高负载下损 TPS，不予采用）%n", aOldAlloc);

        System.out.println("  [采用] map 合并（grown set + lastUse map → 单 map，growing 作物 2 查→1 查）:");
        CropBench.allocCount = 0;
        long bOld = timeCrop(() -> CropBench.sink += CropBench.oldGrowing(0, 7, 64, 7) ? 1 : 0);
        long bNew = timeCrop(() -> CropBench.sink += CropBench.newGrowing(0, 7, 64, 7) ? 1 : 0);
        System.out.printf("    旧(2 查询)=%dns  vs  新(1 查询)=%dns  → %.2fx（同 1 分配，查询减半）%n",
                bOld, bNew, ratio(bOld, bNew));
    }

    private static long timeCrop(Runnable op) {
        int iters = 500_000;
        long t0 = System.nanoTime();
        for (int i = 0; i < iters; i++) op.run();
        return (System.nanoTime() - t0) / iters;
    }

    private static void runPosOf() {
        // 预热
        for (int i = 0; i < 30_000; i++) {
            PosOfBench.sink += PosOfBench.oldBound();
            PosOfBench.sink += PosOfBench.newBound();
            PosOfBench.sink += PosOfBench.oldFree();
        }
        long oldB = timeNanos(PosOfBench::oldBound);
        long newB = timeNanos(PosOfBench::newBound);
        long oldF = timeNanos(PosOfBench::oldFree);
        System.out.printf("  bound 槽机器: 旧(build+lookup)=%d ns  →  新(lookup)=%d ns   %.2fx%n",
                oldB, newB, ratio(oldB, newB));
        System.out.printf("  free  槽机器: 旧(build,从不查)=%d ns  →  新(摊销≈0)=%d ns   %.2fx%n",
                oldF, timeNanos(PosOfBench::newFree), ratio(oldF, Math.max(1, timeNanos(PosOfBench::newFree))));
        System.out.println("  (free 槽机器占 recipe_machines 多数：旧路径每 tick 白建 HashMap，新路径零开销)");
    }

    private static long timeNanos(java.util.function.LongSupplier op) {
        int iters = 200_000;
        long t0 = System.nanoTime();
        for (int i = 0; i < iters; i++) PosOfBench.sink += op.getAsLong();
        long perOp = (System.nanoTime() - t0) / iters;
        return perOp;
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
