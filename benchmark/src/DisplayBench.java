// R5 专项微基准：量化 WTRecipeMachine.getDisplayRecipes 的「展示列表构建」开销。
//   旧：每次调用 new ArrayList + 遍历 recipes 逐项 add（78 配方 = 156 元素列表重建）。
//   新：首次构建后缓存，后续直接返回（提升不变量，同 R2 原则）。
// 调用频率：仅指南打开时（SurvivalSlimefunGuide），非 tick 热路径——绝对收益小，但属正确的「不重复构建不变量」。
package bench;

import java.util.ArrayList;
import java.util.List;

public final class DisplayBench {

    // 78 配方的展示物（每配方 in+out 两项 = 156 元素）
    static final Object[] TEMPLATES = new Object[156];
    static {
        for (int i = 0; i < TEMPLATES.length; i++) TEMPLATES[i] = new Object();
    }

    static List<Object> buildFresh() {
        List<Object> out = new ArrayList<>(TEMPLATES.length);
        for (Object o : TEMPLATES) out.add(o);
        return out;
    }

    static final List<Object> CACHED = buildFresh();
    static List<Object> returnCached() { return CACHED; }

    static long sink = 0;
}
