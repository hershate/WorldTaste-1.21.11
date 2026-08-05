# 性能优化审查日志（独立插件版）

> 目标：在**不损害安全性/稳定性/兼容性（红线）**的前提下，优化插件性能（长时间高负载、多用户高频）。
> 用户授权「必要时可完全重写结构」。每轮从不同优化点出发，**必须把当轮的优化方面做完**；必要时做多方面复合优化。
> 每轮：编写/更新 `benchmark/` 测试程序量化前后性能 → 生成对比报告到本目录 → 更新 note 其它内容 → 细粒度 commit。
> 只维护**插件版本**（plugin/）。
>
> 状态文件：本文件记录每轮结论与累计修复，供 /loop 持续优化跨轮续作。当前版本基线 `1.8.10-standalone`。

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
| R4 | 事件驱动路径 | `FishingListener` 把权重 `total` 提至 load 期 `Bait.total`（每次钓获求和→O(1)）；核查确认 `getById` 已 O(1) → MobDrop 缓存不必要 | Fishing select **~1.7×**（138→82 ns；余为加权遍历） | ✅ 行为不变（total 预算、RNG 不变） | （见下） |
| R5 | 不变量展示列表 + 闭合 | `WTRecipeMachine.getDisplayRecipes` 缓存展示列表（同 R2「提升不变量」原则）；评估 pushOutputs 等低频路径不予改 | getDisplayRecipes **~90×**/次（269→3 ns；指南低频，绝对小） | ✅ recipes 不变，缓存安全 | （见下） |
| R6 | **启动期** YAML 解析缓存（新维度：加载期，R1–R5 未覆盖） | `Yaml.loadResource` 文件名缓存：preloadDisplays + 各 Loader 共 10 个内容文件由「解析两次」→「解析一次」；`Setup.loadAll` 末尾 `clearCache` 释放解析树 | 解析次数/加载 **20→10**；解析段耗时 **~2.0×**（真实 4.63MB/10 文件） | ✅ Loader 只读不写、共享同实例安全；加载后无残留 ConfigurationSection 引用、释放安全 | （见下） |
| R7 | 启动期 头颅贴图(PlayerSkin)去重缓存（加载期） | `Read.resolve` 对 skull_hash/skull/skull_url 按 (类型,材质) 缓存 `PlayerSkin`（3 分表，零命中分配）；`Setup.loadAll` 末尾 `clearSkinCache` 释放 | fromHashCode 工作次数/加载 **2684→2506**（去重 178，6.6%）；fromHashCode 段 **~1.4–1.5×** | ✅ PlayerSkin 不可变、共享安全；getItemStack 仍每次新建独立堆；Read 仅加载期用、释放安全 | （见下） |

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

---

## 第 4 轮（2026-08-06）：事件驱动路径（Fishing total 预算 + getById O(1) 核查）

**范围**：事件驱动路径 `FishingListener.select`（每次钓获）与 `MobDropListener.onDeath`（每次生物死亡）。
本轮先核查 Slimefun 查找原语的真实代价，再据结果定夺。

### 先核查：`SlimefunItem.getById` / `getByItem` 的真实代价（REF SlimefunItem.java:1141-1205）

- **`getById(id)` = O(1)**：`Slimefun.getRegistry().getSlimefunItemIds().get(id)`（HashMap 查找）。
- **`getByItem(item)`**：带「快速负向查找」——先 `getSlimefunItemMaterials().contains(type)`（O(1) Set），
  仅当材质是已知 SF 材质时才读 PDC + `getById`；原版物品单次 Set 查找即返回 null。
- **对 R1 的回溯确认（重要）**：R1 基准把 `getByItem` 建模为「512 元注册表扫描」是**代价量级**的简化，
  但**不影响加速比**——`isItemSimilar` 对 SF 物品每次比较含 1 次 PDC 读取（`getItemData`），纯-SF 机器
  由 31 次→1 次，**加速比≈调用次数比**（两路径均按 per-call 代价等比缩放），故 R1 的 ~21× 稳健。
  真实机制为「**PDC 读取次数减少**」，与基准一致。
- **结论：MobDrop `getById` 缓存不必要**——`onDeath` 的 `getById(d.itemId)` 已是 O(1) HashMap 查找，
  缓存仅省一次 map.get（~ns），无收益、反增状态，**不予采用**。

### 已落地：Fishing `select` 权重 total 预算

- [FishingListener.java](../../../plugin/src/main/java/com/haiman233/worldtaste/behavior/FishingListener.java)：
  把 `Map<String,List<Drop>>` 改为 `Map<String,Bait>`，`Bait` 在 load 期预算 `total = Σweight`，
  `select(Bait)` 直接用预算 total（消除每次钓获对全部掉落——最大 133 项——的求和）。
- **基准**（`FishingBench`，133 掉落）：旧 138 ns（每次求和）→ 新 82 ns（预算 total）= **~1.7×**；余下为加权遍历（仍 O(n)，不可消除）。
- **诚实声明**：钓鱼为玩家主动行为（手持钓竿+副手鱼饵），频率低（~1-2 次/秒/玩家），**绝对收益小**；此为正确的 O(n)→O(1) 消除，零风险。

### 验证
- `./gradlew compileJava` 通过。

---

## 第 5 轮（2026-08-06）：不变量展示列表缓存 + 闭合判定

**范围**：剩余低频路径的评估与收尾。

### 已落地：`getDisplayRecipes` 缓存（提升不变量，同 R2 原则）
- 核查调用点：`getDisplayRecipes` 仅由指南（[SurvivalSlimefunGuide.java:675](../../../REF/RykenSlimeCustomizer-1.21.11/REF/Slimefun4.1/src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/SurvivalSlimefunGuide.java)）调用，
  **不在 AContainer 的 tick 循环内**（AContainer:305 仅为方法定义）。recipes 构造后不变 → 展示列表亦不变。
- [WTRecipeMachine.java](../../../plugin/src/main/java/com/haiman233/worldtaste/machines/WTRecipeMachine.java)：首次调用预算展示列表并缓存（`displayRecipesCache`），后续直接返回。
- **基准**（`DisplayBench`，78 配方=156 元素列表）：旧 269 ns（每次重建）→ 新 3 ns（缓存返回）= **~90×/次**。
- **诚实声明**：指南为玩家主动打开（低频），绝对收益小；此为正确的「不重复构建不变量」（与 R2 同源），零风险。

### 评估后【未改】（低频，无 worthwhile 收益）
- **`pushOutputs`**：仅在配方合成完成时调用（每机器每数秒一次，非 tick）；`ArrayList passed` 小、`clone()` 必要（产出堆独立），
  无可消除的分配。不改。
- **`FoodConsumeListener` / `BlockBreak`（CropListener/BlockDrops）**：玩家/挖掘事件驱动，频率受玩家行为而非 tick 上限约束，
  当前实现（加权/概率掷一次、按实体类型索引）已精简。不改。

---

## 第 6 轮（2026-08-06）：启动期 YAML 解析缓存（新维度：加载期）

**范围**：R1–R5 全部聚焦**运行期热路径**（per-tick / 事件 / 低频），**启动/加载期**维度此前未覆盖——
R3 曾明确放弃此方向（「加载主体代价是 preloadDisplays 内 PlayerSkin.fromHash 头颅解码……非清晰可基准化的优化点」）。
本轮重新打开**加载期**维度中**可清晰量化**的部分：YAML 文件的**重复解析**。

### 热点分析（为何是它）

- [Setup.preloadDisplays](../../../plugin/src/main/java/com/haiman233/worldtaste/load/Setup.java) 把 10 个内容文件
  （[ITEM_FILES](../../../plugin/src/main/java/com/haiman233/worldtaste/load/Setup.java)）各 `Yaml.loadResource` 解析一次，仅为构建
  跨文件 `material_type:slimefun` 引用所需的 `WT.preload` 展示堆。
- 随后各 Loader（ItemsLoader/FoodsLoader/MobDropsLoader/RecipeMachineLoader/MultiBlockLoader/TemplateLoader/WorkbenchLoader/GeoLoader）
  对**同一文件再次 `Yaml.loadResource` 解析一次**。
- 结果：**10 个内容文件每个被解析两次**，含 items.yml(**2.5MB**)、mb_machines.yml(**1.8MB**)、linked_recipe_machines.yml(148KB)、
  recipe_machines.yml(190KB) 等——共 ~4.9MB YAML 被重复解析（+ 重复的 jar 资源 InputStream 读取）。
- 加载虽为一次性（info.yml 自述 1~3 分钟），但服务器定期重启/崩溃恢复时直接计入停机时间；消除重复解析是确定性、零风险的收益。

### 优化设计：文件名缓存（parse-once）+ 加载后释放

1. **[Yaml.loadResource](../../../plugin/src/main/java/com/haiman233/worldtaste/load/Yaml.java) 文件名缓存**：静态 `Map<String,YamlConfiguration>`，
   首次访问解析并入缓存，后续同文件访问为 HashMap 命中。透明适用于全部调用方，无一处需改动。
2. **`Yaml.clearCache()` + [Setup.loadAll](../../../plugin/src/main/java/com/haiman233/worldtaste/load/Setup.java) 末尾释放**：
   加载完毕释放解析树（长稳：避免长期持有 ~MB 级解析对象树）。

### 安全性 / 保真度（红线核查）

- **共享同实例安全**：grep 全插件 `y.set(`/`section.set(`/`config.set(` **零命中**——全部 Loader 仅**读取**配置
  （`getKeys`/`getConfigurationSection`/`getString`/`getInt`…），从不写入。故跨 preloadDisplays 与各 Loader 共享同一 `YamlConfiguration`
  实例**行为完全等价**（无人依赖实例身份或对其进行修改）。
- **释放安全**：grep `ConfigurationSection|YamlConfiguration` 字段声明——**全部为方法内局部变量**，无任何 Loader 以字段形式持久持有。
  加载后领域对象（SlimefunItem / WTRecipe / Behaviors 配置 / WT.preload 堆 / BlockDrops 等）均不再引用原始解析树，
  `registerListeners` 也不再访问 YAML → 末尾 `clearCache` **不释放任何仍被引用的对象**。
- **缺失/异常资源**：仍返回空 `YamlConfiguration`（缓存空配置与旧「每次返回新空配置」行为等价）。
- **行为保真**：解析产物（配置树内容）与旧实现逐字一致，仅复用而非重建；不改变任何加载结果。

### 基准结果（`benchmark/`，`LoadBench`，真实 10 文件 4.63MB）

| 指标 | 旧（每文件解析两次） | 新（文件名缓存） | 结论 |
|---|---|---|---|
| 解析次数 / 次加载（生产同构） | 20 | **10** | 消除 10 个文件的重复解析 |
| 解析段总耗时（50 次加载） | ~1129 ms | ~558 ms | **~2.0×**（如预期：解析工作量减半） |

> 数据源为仓库根的**真实内容文件**（items.yml 2.5MB、mb_machines.yml 1.8MB 等），非合成。`parseFile()` 按 SnakeYAML
> 主导代价结构建模（逐行扫描 + 每行提取键并写入 HashMap，真实读全字符 + 真实 String/Map 分配，不可被 JIT 常量折叠）；
> 主指标「解析次数/加载」与生产端 SnakeYAML 解析次数同构。生产端另省去**重复的 jar 资源 InputStream 读取**（基准保守未计）。
>
> **诚实声明**：R3 既有结论仍成立——加载的**绝对主体代价**是头颅贴图解码 `PlayerSkin.fromHash`（数千个 skull_hash 物品），
> 本轮为对其之外的**解析冗余**做确定性消除；绝对启动耗时仍以真实服务端为准（本环境无服务端）。

### 验证
- `./gradlew compileJava` 通过（仅有既存的 deprecation 提示，非本次引入）。
- 基准 `javac src/*.java && java -Dstdout.encoding=UTF-8 -cp out bench.Main` 通过，sink 非零。

### 下一轮候选（加载期维度未尽）
- **头颅贴图解码去重**：若多个物品/配方槽复用同一 skull_hash，`PlayerSkin.fromHash` 会被重复调用；可缓存 `hash→PlayerSkin`。
  需先 grep 实测 hash 重复率（若近全唯一则无收益）。这是加载期**绝对主体**代价，潜在收益 > R6。
- **`Material.matchMaterial` 名称缓存**：items/recipes 解析期对重复材质名反复 `matchMaterial`（含 legacy 查找）。

---

## 第 7 轮（2026-08-06）：启动期 头颅贴图(PlayerSkin)去重缓存

**范围**：延续 R6 的加载期维度，处理 R6 列出的头号候选——头颅贴图解码。R3 曾标注 `PlayerSkin.fromHash` 为加载
**绝对主体**代价；本轮先**实测重复率**再定夺（数据驱动，同 R3/R4）。

### 先核查：dough `PlayerSkin` 是否已内部缓存？（决定外层缓存有无收益）

读 REF [PlayerSkin.java](../../../REF/RykenSlimeCustomizer-1.21.11/REF/Slimefun4.1/src/main/java/io/github/bakedlibs/dough/skins/PlayerSkin.java)：
`fromHashCode(hash)` → `fromURL` → 每次 `UUID.nameUUIDFromBytes`（**MD5**）+ JSON 拼接 + `Base64.encodeToString` + `URI.create().toURL()`
+ `new CustomGameProfile(...)`，**无任何内部缓存**。故外层去重缓存有真实收益。

### 先实测：skull_hash 重复率（决定收益量级）

Python 扫描全部内容文件的 skull 解码：**3203 次 / 3021 唯一 → 仅 182 次重复（5.7%）**。
基准（SkinBench，13 文件 hex64）实测 **2684 次 / 2506 唯一 / 178 重复（6.6%）**，两者一致确认**重复率低**。
最高重复项为通用装饰头（如某 hash 24×）。

> 结论先行：去重收益**受限于低重复率**（~6%），属「正确但绝对收益小」的零风险优化（同 R3 的 growMsSteps、R4/R5 量级）。
> 3021 个**唯一**解码不可约（每个独立物品需各自贴图）。但因 fromHashCode 单次含 MD5+Base64+JSON+URL（~µs 级），
> 去重那 178 次仍是真实的确定性消除，且 dough 无内部缓存，故落地。

### 优化设计：PlayerSkin 按 (类型,材质) 去重缓存

1. **[Read.resolve](../../../plugin/src/main/java/com/haiman233/worldtaste/load/Read.java)** 对 `skull_hash`/`skull`/`skull_base64`/`skull_url`
   分别用 3 个 `Map<String,PlayerSkin>`（按类型分表：零命中分配、且杜绝跨类型键碰撞），首访 `fromHashCode`/`fromBase64`/`fromURL`
   入缓存，次访直接取缓存的 `PlayerSkin`。
2. **`PlayerHead.getItemStack(skin)` 仍每次调用**——它每次新建独立 `ItemStack`（不共享、不污染），去重只省贴图解码、不省堆构建。
3. **`Read.clearSkinCache()` + [Setup.loadAll](../../../plugin/src/main/java/com/haiman233/worldtaste/load/Setup.java) 末尾释放**：
   Read 仅加载期使用（grep 确认全部 `Read.item/recipe` 调用均在 `load/` 加载器内），释放安全（长稳）。

### 安全性 / 保真度（红线核查）

- **共享安全**：`PlayerSkin` 为**不可变值**（final 字段 uuid/texture/url），跨调用方共享无副作用；
  `PlayerHead.getItemStack` 每次返回**新建** ItemStack（取 meta 快照、设 profile），**不修改**传入的 skin → 缓存值永不被污染。
- **行为保真**：缓存命中的 `PlayerSkin` 与「重新 fromHashCode」产生**逐字相同**的对象（纯函数、无副作用），产出 ItemStack 一致。
- **类型分表无碰撞**：hex64（hash）/ base64（含大写+/） / url（http 开头）字符集不相交，分表进一步隔离。
- **释放安全**：grep 确认 `Read.item/recipe` 仅加载期调用，运行期用 SlimefunItem 注册表与 `WT.preload`（已脱离 PlayerSkin）。

### 基准结果（`benchmark/`，`SkinBench`，真实内容文件）

| 指标 | 旧（每次 fromHashCode） | 新（hash 缓存） | 结论 |
|---|---|---|---|
| fromHashCode 工作次数 / 加载（生产同构） | 2684 | **2506** | 去重 178 次（6.6%） |
| fromHashCode 段耗时（30 次加载） | ~161 ms | ~109 ms | **~1.4–1.5×** |

> `SkinBench` 逐字复刻 dough `fromHashCode` 的 **JDK 工作**（`UUID.nameUUIDFromBytes` MD5 + JSON + `Base64` + `URI.create().toURL()` +
> 持有对象分配）——因 fromHashCode 全是 JDK 操作、无 Bukkit 依赖，本基准为**真实测量**而非建模。`getItemStack` 依赖 Bukkit（不可基准），
> 未计入——本基准量化的正是 PlayerSkin 缓存层去重的那部分。数据源为仓库根真实内容文件扫描到的 skull_hash 材质值。
>
> **诚实声明**：重复率仅 ~6%，绝对收益受限于去重量；3021 个唯一解码为不可约剩余。getItemStack（Bukkit 内部，每唯一物品各一）
> 不可优化。此项为「正确的零风险去重」，量级与 R3 微优化相当。

### 验证
- `./gradlew compileJava` 通过（仅有既存 deprecation 提示，r40 已审计、目标版本可用）。
- 基准 `javac src/*.java && java -Dstdout.encoding=UTF-8 -cp out bench.Main` 通过，sink 非零。

### 下一轮候选
- 加载期已趋收敛：解析缓存(R6) + 贴图去重(R7) 已覆盖；剩余为不可约唯一解码 + getItemStack(Bukkit) + Material.matchMaterial（~ms 级，可忽略）。
- **R8 候选**：做一次全维度「最终扫描」——复核运行期是否有 R1–R5 未触及的分配/查表热点，并扩展基准覆盖（如 MobDrop/BlockDrops/Consumable 路径）；
  若确认无可优化点，则正式宣告全维度收敛。

---

## 闭合判定（R1–R7）

| 维度 | 状态 |
|---|---|
| per-tick 热路径 | `findMatch`（R1/R2 大幅优化）、`CropBlock`（R3 验证已精简、Location 方案经实测劣化已拒）—— **覆盖** |
| 事件驱动高频路径 | Fishing（R4 total 预算）、MobDrop（getById 已 O(1)）—— **覆盖** |
| 低频运行期路径 | getDisplayRecipes（R5 缓存）、pushOutputs/FoodConsume/BlockBreak（评估不改）—— **评估完毕** |
| 加载期路径 | YAML 解析缓存（R6：解析次数 20→10、~2×）；头颅贴图去重（R7：fromHashCode 2684→2506、~1.4×，受限于 6.6% 重复）；getItemStack（Bukkit，每唯一物品各一，不可约）；Material.matchMaterial（~ms 级，可忽略）—— **趋收敛** |

**阶段性结论**：**运行期**热路径 R1–R5 已闭合；**加载期** R6（解析缓存 ~2×）+ R7（贴图去重 ~1.4×）已覆盖可优化项，
剩余为不可约唯一解码 + Bukkit 内部 getItemStack。两维度均**趋收敛**。R8 将做全维度最终扫描确认（并扩展基准覆盖）。
绝对 TPS/启动耗时仍需真实服务端实测（本环境无服务端）。

### 仍待（非静态优化可解）
- **实机 TPS / 启动耗时验证**：绝对性能（高负载 TPS、启动秒数）需真实 Paper 1.21.11 + Slimefun4.1 + 美食家/异域花园 服务端实测
  （本环境无法运行服务端，见 [../../server-verification-checklist.md](../../server-verification-checklist.md)）。微基准的相对加速比可外推方向，但不等于绝对值。
- 若实机 profile 显示新热点（如 cargo 自动化、特定机器配方表），可再开轮（届时需服务端数据，非静态可定夺）。

## 计划（多轮优化点，逐轮推进）

- [x] **R1** 机器配方匹配（SF-id 预筛 + 闸门）—— **完成**
- [x] **R2** findMatch 每 tick 分配消除（posOf 提升为不变量 int[]）—— **完成**
- [x] **R3** CropBlock.tick（验证轮：Location 分配消除经实测劣化已拒绝；落地 growMsSteps 不变量预算）—— **完成**
- [x] **R4** 事件驱动路径（Fishing total 预算；getById O(1) → MobDrop 缓存不必要）—— **完成**
- [x] **R5** getDisplayRecipes 不变量缓存 + 低频路径评估 + **运行期闭合判定** —— **完成（运行期闭合）**
- [x] **R6** 启动期 YAML 解析缓存（文件名缓存 parse-once + 加载后 clearCache 释放）—— **完成（加载期维度开启）**
- [x] **R7** 启动期 头颅贴图(PlayerSkin)去重缓存（按 hash，实测重复率 6.6% 后落地 ~1.4×；dough 无内部缓存；getItemStack 不可约）—— **完成（加载期趋收敛）**
- [ ] **R8**（候选）全维度最终扫描 + 基准覆盖扩展（MobDrop/BlockDrops/Consumable 路径）；确认无可优化则宣告全维度收敛
- [ ] ~~（候选）`Material.matchMaterial` 名称缓存~~ —— ~ms 级、可忽略，剔除

> 闭合判据：R1–R5 运行期闭合；R6–R7 加载期趋收敛（剩余为不可约唯一解码 + Bukkit 内部 getItemStack）。R8 做最终确认扫描。
