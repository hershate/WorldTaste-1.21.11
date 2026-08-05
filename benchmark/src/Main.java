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

        System.out.println("\n[R4] Fishing select（每次钓获）");
        runFishing();

        System.out.println("\n[R5] getDisplayRecipes（每次打开指南）");
        runDisplay();

        System.out.println("\n[R6] 启动期 YAML 解析缓存（每次加载）");
        runLoad();

        System.out.println("\n[R7] 启动期 头颅贴图(PlayerSkin)去重缓存（每次加载）");
        runSkin();

        System.out.println("\n[R8] 作物收获加权选择 total 预算（对齐 R4）+ 最终扫描");
        runHarvest();

        System.out.println("\n(busywork sink=" + Cost.sink + " —— 非零证明代价未被死码消除)");
    }

    private static void runDisplay() {
        for (int i = 0; i < 20_000; i++) {
            DisplayBench.sink += DisplayBench.buildFresh().size();
            DisplayBench.sink += DisplayBench.returnCached().size();
        }
        long fresh = timeOp(() -> DisplayBench.sink += DisplayBench.buildFresh().size());
        long cached = timeOp(() -> DisplayBench.sink += DisplayBench.returnCached().size());
        System.out.printf("  旧(每次重建156元列表)=%dns  vs  新(缓存返回)=%dns  → %.0fx（每次构建消除；指南低频，绝对小）%n",
                fresh, cached, ratio(fresh, cached));
    }

    private static void runFishing() {
        for (int i = 0; i < 20_000; i++) {
            FishingBench.sink += FishingBench.selectOld(FishingBench.DROPS) == null ? 0 : 1;
            FishingBench.sink += FishingBench.selectNew(FishingBench.BAIT) == null ? 0 : 1;
        }
        long oldNs = timeOp(() -> FishingBench.sink += FishingBench.selectOld(FishingBench.DROPS) == null ? 0 : 1);
        long newNs = timeOp(() -> FishingBench.sink += FishingBench.selectNew(FishingBench.BAIT) == null ? 0 : 1);
        System.out.printf("  旧(每次求和133)=%dns  vs  新(预算total)=%dns  → %.2fx（O(n)→O(1)，绝对小但零风险）%n",
                oldNs, newNs, ratio(oldNs, newNs));
    }

    private static void runHarvest() {
        // 预热
        for (int i = 0; i < 20_000; i++) {
            HarvestBench.sink += HarvestBench.selectOld(HarvestBench.DROPS_13);
            HarvestBench.sink += HarvestBench.selectNew(HarvestBench.DROPS_13, HarvestBench.TOTAL_13);
            HarvestBench.sink += HarvestBench.selectOld(HarvestBench.DROPS_50);
            HarvestBench.sink += HarvestBench.selectNew(HarvestBench.DROPS_50, HarvestBench.TOTAL_50);
        }
        System.out.println("  [最终扫描] MobDrop(按实体类型索引+getById O(1)+独立chance)=已最优; "
                + "pushOutputs(clone()必需,base跨合成共享)=R5判断正确; 仅 CropBlock.onBreak 遗留 R4 模式。");
        System.out.println("  作物加权选择（crops.yml 实测：24 加权作物，掉落表 max=13/avg=2.54）:");
        long o13 = timeOp(() -> HarvestBench.sink += HarvestBench.selectOld(HarvestBench.DROPS_13));
        long n13 = timeOp(() -> HarvestBench.sink += HarvestBench.selectNew(HarvestBench.DROPS_13, HarvestBench.TOTAL_13));
        System.out.printf("    n=13(真实max): 旧(每次求和)=%dns  vs  新(load期预算total)=%dns  → %.2fx%n",
                o13, n13, ratio(o13, n13));
        long o50 = timeOp(() -> HarvestBench.sink += HarvestBench.selectOld(HarvestBench.DROPS_50));
        long n50 = timeOp(() -> HarvestBench.sink += HarvestBench.selectNew(HarvestBench.DROPS_50, HarvestBench.TOTAL_50));
        System.out.printf("    n=50(stress):  旧(每次求和)=%dns  vs  新(load期预算total)=%dns  → %.2fx%n",
                o50, n50, ratio(o50, n50));
        System.out.println("  说明：消除求和遍历(O(n)→O(1))；加权选择本身的随机遍历(avg n/2)不可消除。"
                + "收获为玩家事件驱动(低频)，绝对收益小，零风险一致性修复。");
    }

    private static void runSkin() {
        // 预热
        for (int i = 0; i < 3; i++) {
            long[] s = { 0 };
            SkinBench.oldDecode(s);
            SkinBench.newDecode(s);
            Cost.sink += s[0];
        }
        int ITERS = 30;
        long[] sink = { 0 };
        long t0 = System.nanoTime();
        int oldWork = 0;
        for (int i = 0; i < ITERS; i++) oldWork += SkinBench.oldDecode(sink);
        long oldNs = System.nanoTime() - t0;
        t0 = System.nanoTime();
        int newWork = 0;
        for (int i = 0; i < ITERS; i++) newWork += SkinBench.newDecode(sink);
        long newNs = System.nanoTime() - t0;
        Cost.sink += sink[0];
        int total = SkinBench.HASHES.size();
        int uniq = SkinBench.UNIQUE;
        int redundant = total - uniq;
        System.out.printf("  数据源: %s（扫描到 %d 个 skull_hash 解码，唯一 %d，重复 %d = %.1f%%）%n",
                SkinBench.usedRealFiles ? "仓库根真实内容文件" : "合成代表分布（未找到真实文件）",
                total, uniq, redundant, total == 0 ? 0 : 100.0 * redundant / total);
        System.out.printf("  fromHashCode 工作次数/加载: 旧=%d  新=%d   （缓存命中重复项）%n",
                oldWork / ITERS, newWork / ITERS);
        System.out.printf("  fromHashCode 段耗时(%d 次加载): 旧=%.2fms  新=%.2fms  → %.2fx%n",
                ITERS, oldNs / 1e6, newNs / 1e6, ratio(oldNs, newNs));
        System.out.println("  说明：基准逐字复刻 fromHashCode 的 JDK 工作(MD5+JSON+Base64+URL)；getItemStack 依赖 Bukkit 未计入。");
        System.out.printf("        重复率仅 %.1f%%，故绝对收益受限于去重量；3021 个唯一解码为不可约剩余。%n",
                total == 0 ? 0 : 100.0 * redundant / total);
    }

    private static void runLoad() {
        // 预热
        for (int i = 0; i < 5; i++) {
            long[] s = { 0 };
            LoadBench.oldLoad(s);
            LoadBench.newLoad(s);
            Cost.sink += s[0];
        }
        int ITERS = 50;
        long[] sink = { 0 };
        long t0 = System.nanoTime();
        int oldParses = 0;
        for (int i = 0; i < ITERS; i++) oldParses += LoadBench.oldLoad(sink);
        long oldNs = System.nanoTime() - t0;
        t0 = System.nanoTime();
        int newParses = 0;
        for (int i = 0; i < ITERS; i++) newParses += LoadBench.newLoad(sink);
        long newNs = System.nanoTime() - t0;
        Cost.sink += sink[0];
        System.out.printf("  数据源: %s（%.2f MB，10 文件）%n",
                LoadBench.usedRealFiles ? "仓库根真实内容文件" : "合成代表内容（未找到真实文件）",
                LoadBench.TOTAL_BYTES / 1048576.0);
        System.out.printf("  解析次数/次加载: 旧=%d  新=%d   （10 文件 ×2 → ×1，余为缓存命中）%n",
                oldParses / ITERS, newParses / ITERS);
        System.out.printf("  总耗时(%d 次加载): 旧=%.1fms  新=%.1fms  → %.2fx%n",
                ITERS, oldNs / 1e6, newNs / 1e6, ratio(oldNs, newNs));
        System.out.println("  说明：消除 10 个内容文件的重复解析（生产端还省去重复的 jar 资源读取）。");
        System.out.println("        R3 既有结论：加载主体代价仍为头颅贴图解码(PlayerSkin.fromHash)；本项为零风险确定性消除。");
    }

    private static long timeOp(Runnable op) {
        int iters = 500_000;
        long t0 = System.nanoTime();
        for (int i = 0; i < iters; i++) op.run();
        return (System.nanoTime() - t0) / iters;
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
