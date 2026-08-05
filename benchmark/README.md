# WorldTaste 性能微基准（benchmark/）

> 量化插件热路径优化前后的性能。**无外部依赖**（仅需 JDK 21+），不依赖 Bukkit/Paper/Slimefun 服务端。
> 设计方法论与诚实声明见 [../note/report/perf/PERF-AUDIT.md](../note/report/perf/PERF-AUDIT.md)。

## 运行

```bash
./run.sh        # Linux/macOS/Git-Bash
# 或
run.bat         # Windows
```

输出每次「匹配尝试」的耗时（ns）、`similarity` 调用数（对应生产端 `SlimefunUtils.isItemSimilar` 调用数）、
`resolve` 调用数（每 tick 每输入槽解析 SF id 的次数），以及加速比。

## 文件

| 文件 | 作用 |
|---|---|
| `src/SimItem.java` | `ItemStack` 的简化替身（`type`≈Material、`sfId`≈Slimefun PDC id） |
| `src/Recipe.java` | `WTRecipe` 匹配相关字段的替身（inputs/inSlots/noConsume/needSfId） |
| `src/Cost.java` | 成本模型 + 计数器：`similarity()` 复刻 `isItemSimilar` 代价结构；`resolveSlotId()` 复刻读 PDC |
| `src/Matcher.java` | 匹配算法两实现 + 闸门：`matchLinear`（现状）、`matchPruned`（SF-id 预筛）、`matchGated`（机器级闸门） |
| `src/Scenarios.java` | 依真实 recipe_machines.yml 形态生成的代表性场景 |
| `src/LoadBench.java` | R6 启动期 YAML 解析缓存基准：用仓库根【真实 10 个内容文件】量化解析次数(20→10)/加载与耗时 |
| `src/Main.java` | 预热 + 自适应计时主程序 |
| `results.txt` | 最近一次运行输出（仅供参考；报告以多次运行的中位趋势为准） |

## 方法论要点

- `similarity()` 按 REF `SlimefunUtils.isItemSimilar`（行 350-376）的代价结构建模：类型/数量校验廉价；
  类型匹配后调用两次 `getByItem`（昂贵，以「扫描数据相关的注册表数组」模拟，**不可被 JIT 常量折叠**）。
- 主指标 `similarity 调用数/次` 与生产端 `isItemSimilar` 调用数**同构**（同一算法），可直接外推生产收益。
- 绝对生产 TPS 仍需真实服务端实测（与本仓库「本环境无法运行服务端」的既有限制一致，见
  [../note/server-verification-checklist.md](../note/server-verification-checklist.md)）。
