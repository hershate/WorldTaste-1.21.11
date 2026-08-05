// R4 专项微基准：量化 FishingListener.select 的「按权重随机」每次调用开销。
//   旧（现状）：每次钓鱼 select(drops) 都对全部掉落 weight 求和（133 掉落 = 133 次加法）。
//   新：load 时预算每个鱼饵的 total，select 直接用预算值（O(1)）。
// 说明：钓鱼为玩家主动行为（手持钓竿+副手鱼饵），频率低（~1-2 次/秒/玩家），故绝对收益小，
//      但属正确的 O(n)→O(1) 消除，零风险。
package bench;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class FishingBench {

    static final class Drop { final String id; final int weight; Drop(String id, int w) { this.id = id; this.weight = w; } }
    static final class Bait { final List<Drop> drops; final int total; Bait(List<Drop> d) { this.drops = d; int t = 0; for (Drop x : d) t += x.weight; this.total = t; } }

    // 133 掉落（对齐 diaoyu.js 最大鱼饵表规模）
    static final List<Drop> DROPS = new ArrayList<>();
    static final Bait BAIT;
    static {
        for (int i = 0; i < 133; i++) DROPS.add(new Drop("WT_FISH_" + i, 1 + (i % 10)));
        BAIT = new Bait(DROPS);
    }

    // 旧：每次求和
    static Drop selectOld(List<Drop> drops) {
        int total = 0;
        for (Drop d : drops) total += d.weight;
        if (total <= 0) return null;
        double r = ThreadLocalRandom.current().nextDouble() * total;
        for (Drop d : drops) { r -= d.weight; if (r <= 0) return d; }
        return drops.get(drops.size() - 1);
    }

    // 新：用预算 total
    static Drop selectNew(Bait bait) {
        if (bait.total <= 0) return null;
        double r = ThreadLocalRandom.current().nextDouble() * bait.total;
        for (Drop d : bait.drops) { r -= d.weight; if (r <= 0) return d; }
        return bait.drops.get(bait.drops.size() - 1);
    }

    static long sink = 0;
}
