# 性能优化审查日志（独立插件版）

> 目标：在**不损害安全性/稳定性/兼容性（红线）**的前提下，优化插件性能（长时间高负载、多用户高频）。
> 用户授权「必要时可完全重写结构」。每轮从不同优化点出发，**必须把当轮的优化方面做完**；必要时做多方面复合优化。
> 每轮：编写/更新 `benchmark/` 测试程序量化前后性能 → 生成对比报告到本目录 → 更新 note 其它内容 → 细粒度 commit。
> 只维护**插件版本**（plugin/）。
>
> 状态文件：本文件记录每轮结论与累计修复，供 /loop 持续优化跨轮续作。当前版本基线 `1.8.3-standalone`。

## 方法论与诚实声明

- **基准性质**：`benchmark/` 为**算法微基准**，无 Bukkit/服务端依赖（本环境无法运行 Paper+Slimefun，见
  [../../server-verification-checklist.md](../../server-verification-checklist.md)）。`similarity()` 按 REF
  `SlimefunUtils.isItemSimilar`（[SlimefunUtils.java:350-376](../../../REF/RykenSlimeCustomizer-1.21.11/REF/Slimefun4.1/src/main/java/io/github/thebusybiscuit/slimefun4/utils/SlimefunUtils.java)）的**代价结构**建模
  （类型/数量校验廉价；类型匹配后 2× `getByItem` 昂贵，以「扫描数据相关注册表数组」模拟、不可被 JIT 常量折叠）。
- **主指标 `similarity 调用数/次`** 与生产端 `isItemSimilar` 调用数**同构**（同一算法），可直接外推生产收益；
  耗时为次指标。**绝对生产 TPS 仍需真实服务端实测**（已知缺口，与既有审查一致）。
- **红线**：任一优化须 (1) 行为保持（对齐 RSC 保真度，见 [../../security-audit.md](../../security-audit.md)）；
  (2) 不引入复制/吞物品/级联崩溃等数据安全问题；(3) **对未优化的机器/路径零回归**（基准须显示 ≥1.00x）。

## 累计状态

| 轮 | 优化点 | 生产改动 | 基准收益（代表值） | 零回归 | commit |
|---|---|---|---|---|---|
| R1 | 机器配方匹配热路径 | `WTRecipe` 惰性 `inputSfId` + `WTRecipeMachine` 纯-SF 闸门 + SF-id 预筛 | 纯-SF 机器 **7.5~26×**（isItemSimilar 调用 31→0/2、200→0/2） | ✅ 非-SF 机器 1.00×（闸门关闭） | （见下） |
| R2 | findMatch 每 tick 分配消除 | `WTRecipeMachine` 把不变量 `posOf`（每 tick `new HashMap`+Integer 装箱）提至构造期 `int[] posBySlot` | 绑定槽机器 **~2.1×**、自由槽机器 **~17×**（白建 HashMap 消除） | ✅ 行为不变（posOf 仅依赖 inputSlots） | （见下） |
| R3 | CropBlock.tick（验证轮 + 微优） | `CropBlock` 预算 `growMsSteps`（不变量，避免每 tick 8 次乘法） | 微（生长作物每 tick 省 8 乘法）；**Location 分配消除方案经实测为 CPU 劣化（0.54×）已拒绝** | ✅ 行为逐字一致（double 阈值保精确语义） | （见下） |

---

## 第 1 轮（2026-08-05）：机器配方匹配热路径（findMatch）

**范围**：`WTRecipeMachine.findMatch`（电力配方机 / 模板机每 tick 匹配输入）。这是高负载下最显著的可优化热路径。

### 热路径分析（为何是它）

- `AContainer.tick` 在「无在合成操作」时每 tick（10Hz）调用 `findNextRecipe → findMatch`。
- 最坏情形：机器**输出被占满**或**持有不匹配输入**时，每 tick 全量扫描该机器所有配方，每比较一次调
  `SlimefunUtils.isItemSimilar(in, need, true)`。
- `isItemSimilar`（REF 行 350-376）在**类型匹配后**会调用 `SlimefunItem.getByItem()` **两次**（item 与 sfitem 各一）——
  对 SF 头颅类物品（材质同为 `PLAYER_HEAD`、仅 SF id 不同）而言，**每条配方比较都付此代价**。
- 真实规模：`WT_SHUIZUXIANG` 78 配方、`WT_HWSCPCLJ` 31 配方、`WT_CHANLUANSHI` 21 配方等（见下方闸门表）。

### 优化设计：SF-id 预筛 + 纯-SF 机器闸门

1. **`WTRecipe.inputSfId(i)`**（[WTRecipe.java](../../../plugin/src/main/java/com/haiman233/worldtaste/machines/WTRecipe.java)）：
   惰性预解析每个输入的 SF id（`SlimefunItem.getByItem(need).getId()`，原版输入为 null），缓存。
2. **每 tick 预解析各输入槽 SF id**（`resolveSlotSfIds`，读 PDC `Slimefun.getItemDataService().getItemData`）——
   每槽**每 tick 一次**，而非每次比较一次。这是收益来源。
3. **廉价必要条件预筛 `idCertainlyMismatch`**：`inId != null && needId != null && !inId.equals(needId)` 为真时
   **跳过** `isItemSimilar`（两端均 SF 且 id 不同 → `isItemSimilar` 的 both-SF 分支必返回 false）。
4. **机器级闸门 `sfPrune`**（`computeSfPrune`）：仅当「≥2 配方 且 所有非空输入均为 SF 物品」时启用；
   否则 `slotSfId=null`、`!(sfPrune && …)` 短路为 true，**代码路径与优化前逐字一致（零回归）**。

### 安全性 / 保真度（红线核查）

- **跳过的安全性证明**：仅在「两端均已解析为 SF 物品且 id 不同」时跳过；此情形下 `isItemSimilar` 的 both-SF 分支
  （REF 行 363-366）按 id 比较必返回 false，故跳过不改变任何匹配结果。其余情形（任一原版、id 相同、无 PDC、
  DistinctiveItem）一律仍走 `isItemSimilar` 定夺 —— **完整保留 RSC 保真度与 r27 的 first-match/distinct 语义**。
- **闸门零回归**：非纯-SF 机器（原版材质主导、类型短路已廉价；或混合机器原版输入先行 gating）预筛无收益反增开销，
  故闸门对其关闭 → 逐字回退原算法。基准实测 SHUIZUXIANG(78 原版)/FUHUASHI(22 混合) 均 **1.00×**。
- **`stillValid` 复校不变**：仍对选中槽实时再 `isItemSimilar`（不用预解析 id），竞态防护不变（r1）。
- **惰性解析无加载期回归**：`inputSfId` 仅在闸门检查 / 启用机器的 tick 中解析；多方块机不经过此路径不付出开销。

### 基准结果（benchmark/，JDK 21.0.12 / 16 核，代表值；详见 results.txt）

| 场景（对应真实机器形态） | 闸门 | 用例 | Linear 现状 | Optimized | 加速 |
|---|---|---|---|---|---|
| SHUIZUXIANG 78 单输入·原版材质 | off | noMatch/hitLast | ns≈880~1381, sim=78~79 | = Linear | **1.00×** |
| HWSCPCLJ 31 单输入·SF 头颅 | **ON** | noMatch | ns=9206, sim=31 | ns=439, sim=**0**, res=1 | **~21×** |
| HWSCPCLJ 31 单输入·SF 头颅 | **ON** | hitLast | ns=9497, sim=32 | ns=1112, sim=**2**, res=1 | **~8.5×** |
| FUHUASHI 22 双输入·混合 | off | noMatch/hitLast | ns≈7638~13137, sim=88~89 | = Linear | **1.00×** |
| STRESS 200 单输入·SF 头颅（压力放大） | **ON** | noMatch | ns=62480, sim=200 | ns=2369, sim=**0**, res=1 | **~26×** |
| STRESS 200 单输入·SF 头颅 | **ON** | hitLast | ns=60723, sim=201 | ns=3016, sim=**2**, res=1 | **~20×** |

> 解读：纯-SF 机器的 `isItemSimilar`（含 2× getByItem）调用数从 O(配方数) 降至 O(命中数)；耗时随配方数线性下降。
> 原/混合机器逐字不变。`busywork sink` 非零证明代价未被 JIT 死码消除。

### 生产闸门映射（真实 recipe_machines.yml + template_machines.yml，PyYAML 实测）

- **闸门 ON（受益）**：WT_ZHONGZIJDY(15)、WT_NGZHONGZIJDY(5)、WT_HWSCPCLJ(31)、WT_XIELONG(7)、WT_CHANLUANSHI(21,模板)、WT_TUZAIJI(2,模板)。
- **闸门 off（零回归）**：WT_SHUIZUXIANG(78)、WT_FUHUASHI(22)、WT_BUYUWANG、WT_TONGCANG、WT_SHIPINYLJGJ、WT_RENZAOROUHCJ(_V1~V5)、WT_BEICHALU、WT_DIANCHAHU、WT_SHUICHANPEIYUJI 等。

### 验证

- `./gradlew compileJava` 通过（BUILD SUCCESSFUL）。
- 基准 `javac src/*.java && java bench.Main` 通过，sink 非零。

### 待办（后续轮次候选，非本轮范围）

- 原/混合机器的线性扫描仍为 O(配方数) 廉价类型短路——若需进一步降常数可考虑 Material 索引（R 候选）。
- 其它热路径：`pushOutputs` 分配抖动、`CropBlock.tick`、加载期 `Read.item` 双趟、MobDrop/Fishing 等（见本文件「计划」）。

---

## 第 2 轮（2026-08-05）：findMatch 每 tick 分配消除（posOf 提升为不变量）

**范围**：与 R1 同一热路径（`WTRecipeMachine.findMatch`），但**从分配/装箱角度**切入（复合优化）。输出阻塞机器每 tick 触发 findMatch，其首部每调用 `new HashMap<Integer,Integer>` + `slotCount` 次 `put`（Integer 装箱 + Node 分配）。

### 优化设计

`posOf`（输入槽 GUI 索引 → inputSlots 数组位置）**仅依赖 `inputSlots`（final 字段）**，是机器级不变量。R2 前
每 tick 重建；R2 提至构造期一次预算为 `int[] posBySlot`（54 覆盖整个背包尺寸，-1=非输入槽），findMatch 内
仅做 `posBySlot[bound]` 直接索引。

### 安全性 / 保真度（红线核查）

- **行为不变**：`posBySlot[bound]` 与原 `posOf.get(bound)` 在「bound ∈ inputSlots → 位置」「bound ∉ → -1/null → failed」
  上完全等价；越界 bound（≥54 或 <0）回退 -1 → failed，与原 `posOf.get==null` 一致。
- 不影响 R1 的 SF-id 预筛、`stillValid` 复校、first-match/distinct 语义。
- 无新分配、无并发变化（构造期填充，tick 主线程只读）。

### 基准结果（`PosOfBench`，per findMatch 调用）

| 用例 | 旧（R2 前） | 新（R2） | 加速 |
|---|---|---|---|
| 绑定槽机器（build+lookup） | ~58 ns | ~27 ns（仅 lookup） | **~2.1×** |
| 自由槽机器（build，从不查） | ~51 ns | ~3 ns（摊销≈0） | **~17×** |

> 解读：自由槽机器（recipe_machines 多数，inputs 的 `slot` 为自由）旧路径**每 tick 白建 HashMap**（从不查询，
> 因 `bound=-1` 走自由扫描分支）——纯分配浪费；新路径彻底消除。绑定槽机器（linked/workbench）亦省去 per-call
> 重建并改用数组索引。对高负载（多台输出阻塞机器 × 10Hz）显著降低 GC 压力。

### 验证
- `./gradlew compileJava` 通过。基准 `PosOfBench` 通过。

---

## 第 3 轮（2026-08-05）：CropBlock.tick（验证轮 + 不变量预算微优）

**范围**：唯一剩余的「每 tick 每方块」热路径 `CropBlock.tick`（大型农场数百~数千作物 × 10Hz）。
本轮为**数据驱动的验证轮**：先实测候选优化、再据结果定夺，避免引入劣化。

### 先核查：加载期并非双趟解析（否决 R4 旧设想）

读 [ItemsLoader.register](../../../plugin/src/main/java/com/haiman233/worldtaste/load/ItemsLoader.java) 第 79 行：
注册期**直接复用 `WT.preload` 的展示堆**（`new SlimefunItemStack(effId, display)`），**不重调 `Read.item`**。
故「preloadDisplays + ItemsLoader 双趟解析」设想**不成立**——加载期主体代价是 preloadDisplays 内的
`PlayerSkin.fromHashCode` 头颅解码（单趟、Bukkit 内部、可能已内建缓存），非清晰可基准化的优化点。R4 不走此方向。

### 候选优化实测（benchmark/CropBench）

| 候选 | 旧 | 新 | 结论 |
|---|---|---|---|
| **A. Location 分配消除**（`b.getLocation()` 分配 → pack-long + 双层 map，0 分配） | 13 ns（1 分配+1 查） | 24 ns（0 分配+2 查） | **拒绝（0.54×，CPU 翻倍）**：双层 map 的两次查询代价 > 一次 Location 分配；虽省 GC，但 per-tick CPU 翻倍在高负载下损 TPS。**重要负结果：防止后续误引入此劣化。** |
| **B. map 合并**（grown set + lastUse map → 单 map，growing 作物 2 查→1 查） | 11 ns | 11 ns | **1.00×（不可测）**：查询减半但差异落入噪声；不值得为此改动状态结构（红线：无收益不改）。 |
| **C. 不变量预算**（`growMs * SMALL_STEPS[i]` 提至构造期 `double[] growMsSteps`） | 每 tick 8 乘法 | 0 乘法（数组载入） | **采用**：行为逐字一致（double 阈值保精确语义），消除每生长作物每 tick 的 8 次 double 乘法。收益微但零风险。 |

### 已落地
- [CropBlock.java](../../../plugin/src/main/java/com/haiman233/worldtaste/items/CropBlock.java)：构造期预算 `growMsSteps`（`cfg.growMs` 为 `long`，`* SMALL_STEPS[i]` 得 double，存 `double[]` 保精确比较语义），tick 循环改用预算值。

### 阶段性结论（per-tick 热路径收敛）
两个 per-tick 热路径已覆盖：`findMatch`（R1/R2 大幅优化）、`CropBlock`（R3 验证为已精简：成熟作物稳态仅 1 次 Location 分配 + 1 次 `getType` + 1 次集合查询，无明显可量化头寸）。**per-tick 维度趋于收敛**。后续轮次转向事件驱动高频路径（钓鱼/屠宰/指南展示）与分配抖动。

## 计划（多轮优化点，逐轮推进直至闭合）

- [x] **R1** 机器配方匹配（SF-id 预筛 + 闸门）—— **完成**
- [x] **R2** findMatch 每 tick 分配消除（posOf 提升为不变量 int[]）—— **完成**
- [x] **R3** CropBlock.tick（验证轮：Location 分配消除经实测劣化已拒绝；落地 growMsSteps 不变量预算）—— **完成**
- [ ] **R4** 事件驱动高频路径：Fishing 权重 `total` 预算 / MobDrop `getById` 缓存
- [ ] **R5** 分配抖动：`getDisplayRecipes` 缓存 / `pushOutputs` 等
- [ ] **R6+** 收尾与闭合评估

> 闭合判据：可优化热路径均已覆盖，且新增轮次连续无收益（参照 security-audit 的收敛模式）。
