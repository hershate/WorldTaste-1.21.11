// 成本模型 + 计数器：similarity() 忠实复刻 SlimefunUtils.isItemSimilar(item, sfitem, checkLore=true, checkAmount=true)
// 的代价结构（见 REF SlimefunUtils.java:350-376）：
//   1) null/类型/数量 校验 —— 廉价（不触发 getByItem）；
//   2) 类型匹配后调用 SlimefunItem.getByItem(item) 与 getByItem(sfitem) 各一次（昂贵，扫描 SF 注册表）；
//   3) 两端均为 SF 物品则按 id 比较。
// resolveSlotId() 模拟每 tick 对每个非空输入槽读一次 PDC（Slimefun.getItemDataService().getItemData）。
//
// 关键：用「扫描数据相关的注册表数组」模拟 getByItem，而非可被 JIT 折叠为闭式的算术循环——
// 这样每次调用的代价是真实、稳定且随调用次数线性叠加的（否则 C2 会把 sum(i*i) 常量折叠，使预筛
// 跳过的调用显示为 0 开销，基准失真）。
package bench;

public final class Cost {

    /** 模拟 Slimefun SF 物品注册表：getByItem 在其上线性扫描（带缓存，但非零代价）。 */
    static final int[] REGISTRY = new int[512];
    static {
        for (int i = 0; i < REGISTRY.length; i++) REGISTRY[i] = (i * 0x9E3779B1);
    }

    static long similarityCalls = 0;
    static long resolveCalls = 0;
    static long sink = 0; // 防止死码消除

    static void resetCounters() {
        similarityCalls = 0;
        resolveCalls = 0;
    }

    /** 数据相关扫描（不可常量折叠）：在 REGISTRY 上线性查找命中 key 的项并累加。模拟 getByItem 的注册表扫描。 */
    private static int getByItemModel(int key) {
        int x = 0;
        int k = key & 0x1FF;
        for (int i = 0; i < REGISTRY.length; i++) {
            if ((REGISTRY[i] & 0x1FF) == k) x += REGISTRY[i];
        }
        return x;
    }

    private static int keyOf(SimItem it) {
        return it.type * 1000003 + (it.sfId == null ? 0 : it.sfId.hashCode());
    }

    /**
     * 模拟 isItemSimilar(in, need, true)。每次调用计数+1（生产端每次比较都调一次 isItemSimilar）。
     */
    static boolean similarity(SimItem in, SimItem need) {
        similarityCalls++;
        if (in == null) return need == null;
        if (need == null) return false;
        if (in.type != need.type) return false;        // 廉价：类型不符直接 false（不触发 getByItem）
        if (in.amount < need.amount) return false;     // 廉价：数量不足
        sink += getByItemModel(keyOf(in));             // 昂贵：getByItem(item)
        sink += getByItemModel(keyOf(need));           // 昂贵：getByItem(sfitem)
        if (in.sfId != null && need.sfId != null) {
            return in.sfId.equals(need.sfId);          // both-SF 分支：按 id 比较
        }
        return in.sfId == null && need.sfId == null;   // 近似 meta 相等
    }

    /**
     * 模拟每 tick 对一个非空输入槽解析其 SF id（读 PDC）。每次调用计数+1；空槽不计。
     */
    static String resolveSlotId(SimItem item) {
        if (item == null) return null;
        resolveCalls++;
        sink += getByItemModel(keyOf(item));           // 昂贵：读 PDC（每槽每 tick 一次）
        return item.sfId;
    }
}
