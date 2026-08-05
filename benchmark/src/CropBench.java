// R3 专项微基准：量化 CropBlock.tick 的「每 tick 每作物」状态查询开销。
// 成熟作物（稳态、占多数）每 tick：取状态 → 检查 grown → 返回。
//   旧：b.getLocation() 分配一个 Location 对象 + grown.contains(l)（1 次 set 查询）。
//   新：getWorld()(缓存,无分配) + states.get(world).get(pack(x,y,z))（0 分配 + 2 次 map 查询）。
// 主指标：分配数/op（Location 对象 → 0，直接映射 GC 压力）；次指标：耗时/op。
// 说明：TLAB 内分配很快，单次微基准耗时差异小；GC 压力（长时间高负载的关键）以「分配数/op」诚实量化。
package bench;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CropBench {

    /** 模拟 Bukkit Location（不可变，按 world+x+y+z 做 equals/hashCode）。 */
    static final class LocKey {
        final int w, x, y, z;
        LocKey(int w, int x, int y, int z) { this.w = w; this.x = x; this.y = y; this.z = z; }
        @Override public boolean equals(Object o) {
            LocKey k = (LocKey) o; return k.w == w && k.x == x && k.y == y && k.z == z;
        }
        @Override public int hashCode() { return (w * 31 + x) * 31 + y * 31 + z; }
    }

    static long allocCount = 0; // 计 LocationKey 分配

    static long pack(int x, int y, int z) {
        return ((long)(x & 0x3FFFFFF) << 38) | ((long)(z & 0x3FFFFFF) << 12) | ((long)(y & 0xFFF));
    }

    static final class State { boolean grown; long lastUse; }

    // —— 旧：LocationKey 分配 + 2 个集合（grown set + lastUse map，对齐现状双 map）——
    static final Set<LocKey> grownOld = new HashSet<>();
    static final Map<LocKey, Long> lastUseOld = new HashMap<>();
    static {
        for (int i = 0; i < 1000; i++) { grownOld.add(new LocKey(0, i, 64, i)); } // 预填成熟作物
    }
    static boolean oldMature(int w, int x, int y, int z) {
        LocKey l = new LocKey(w, x, y, z);   // 每次分配（对齐 b.getLocation()）
        allocCount++;
        if (grownOld.contains(l)) return true; // 成熟早返回
        Long last = lastUseOld.get(l);
        return last != null;
    }

    // —— 新：pack-long key + 双层 map（world→packedKey→state），0 分配 ——
    static final Map<Integer, Map<Long, State>> statesNew = new HashMap<>();
    static {
        Map<Long, State> m = new HashMap<>();
        for (int i = 0; i < 1000; i++) { State s = new State(); s.grown = true; m.put(pack(i, 64, i), s); }
        statesNew.put(0, m);
    }
    static boolean newMature(int w, int x, int y, int z) {
        Map<Long, State> wm = statesNew.get(w);
        if (wm == null) return false;
        State st = wm.get(pack(x, y, z));
        return st != null && st.grown;
    }

    static long sink = 0;

    // —— map-merge 对比（R3 实际采用的安全优化）：growing 作物路径 ——
    // 旧（现状）：grownSet.contains(l) + lastUseMap.get(l) = 2 次查询（均用同一 LocationKey）
    static boolean oldGrowing(int w, int x, int y, int z) {
        LocKey l = new LocKey(w, x, y, z);
        allocCount++;
        if (grownOld.contains(l)) return true;
        return lastUseOld.containsKey(l);
    }

    // 新（合并为单 map<Location,State>）：stateMap.get(l) = 1 次查询
    static final Map<LocKey, State> stateMerged = new HashMap<>();
    static {
        for (int i = 0; i < 1000; i++) { State s = new State(); s.grown = false; s.lastUse = i; stateMerged.put(new LocKey(0, i, 64, i), s); }
    }
    static boolean newGrowing(int w, int x, int y, int z) {
        LocKey l = new LocKey(w, x, y, z);
        allocCount++;
        State st = stateMerged.get(l);
        return st != null;
    }
}
