// R8 专项微基准：量化「CropBlock 收获加权选择 total 预算」的收益（对齐 R4 FishingListener）。
//
// 背景：R8 最终扫描发现 CropBlock.onBreak 每次收获都对 drops 重新求和权重 total（double 累加 O(n)），
//   而 R4 已把同样的加权选择在 FishingListener 改为 load 期预算 total（Bait.total）。R8 对齐之：
//   CropCfg.weightTotal 在 loadCrops 期预算，onBreak 直接用（O(n)→O(1) 的求和消除）。
//
// 数据：crops.yml 实测 142 作物中 24 个加权，掉落表规模 min=2/max=13/avg=2.54。
//   本基准用真实 max(13) 与 stress(50) 展示；其余 118 个 chance 作物不经加权选择（无 total）。
//
// 方法论（诚实声明）：
//   - 加权选择本身含一次 Math.random() + 遍历（avg n/2），不可消除；R8 仅消除其前的「求和」遍历。
//   - 收获为玩家破坏事件驱动（低频，同钓鱼），绝对收益小；此为正确的 O(n)→O(1) 一致性消除，零风险。
package bench;

public final class HarvestBench {

    static final class Drop {
        final String id;
        final double weight;
        Drop(String id, double weight) { this.id = id; this.weight = weight; }
    }

    /** 真实最大加权掉落表规模（crops.yml 实测 max=13）。 */
    static final Drop[] DROPS_13 = make(13);
    static final double TOTAL_13 = sum(DROPS_13);
    /** 压力放大规模。 */
    static final Drop[] DROPS_50 = make(50);
    static final double TOTAL_50 = sum(DROPS_50);

    static long sink = 0;

    private HarvestBench() {}

    private static Drop[] make(int n) {
        Drop[] d = new Drop[n];
        for (int i = 0; i < n; i++) d[i] = new Drop("WT_DROP_" + i, (i % 7) + 1); // 确定性权重 1..7
        return d;
    }

    private static double sum(Drop[] ds) {
        double t = 0;
        for (Drop d : ds) t += d.weight;
        return t;
    }

    /** 旧：每次收获重新求和 total（R8 前 CropBlock.onBreak）。返回选中 id 的长度（防死码）。 */
    static int selectOld(Drop[] ds) {
        double total = 0;
        for (Drop d : ds) total += d.weight;
        if (total <= 0) return 0;
        double r = Math.random() * total;
        Drop picked = ds[ds.length - 1];
        for (Drop d : ds) { r -= d.weight; if (r <= 0) { picked = d; break; } }
        return picked.id.length();
    }

    /** 新：用 load 期预算的 total（R8 后 CropBlock.onBreak，对齐 R4）。 */
    static int selectNew(Drop[] ds, double total) {
        if (total <= 0) return 0;
        double r = Math.random() * total;
        Drop picked = ds[ds.length - 1];
        for (Drop d : ds) { r -= d.weight; if (r <= 0) { picked = d; break; } }
        return picked.id.length();
    }
}
