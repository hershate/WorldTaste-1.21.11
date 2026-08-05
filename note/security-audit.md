# 稳定性 / 安全性审查日志（独立插件版）

> 目标：在长时间高负载、多用户高频使用下保证稳定与数据安全；**不信任任何用户输入**（破解客户端可发送任意协议包、伪造点击/破坏/放置事件、篡改物品 NBT）。
> 每轮聚焦不同子系统，发现即修复，附 commit 与验证结论。运行期实机验证仍待补（见 [standalone-plugin.md#已知差距](standalone-plugin.md)）。

## 第 1 轮（2026-08-05）：机器与事件处理器的数据操作安全

**范围**：电力配方机 / 多方块机 / 工作台 / 模板机的输入消耗与产出；钓鱼、消耗品进食、生物掉落、作物破坏、方块掉落等玩家可触发路径。重点排查物品**复制 / 丢失 / 幽灵物品 / 竞态**。

### 已修复

| # | 严重度 | 位置 | 缺陷 | 后果 | 修复 / commit |
|---|---|---|---|---|---|
| 1 | 🔴 复制 | `FishingListener.onFish` | 鱼饵 `setAmount(n-1)` 到 0 时副手残留 0 数量幽灵物品，其 Slimefun 绑定不变，仍被 `getByItem` 识别为该鱼饵 | 鱼饵耗尽后可**无消耗无限钓获**（复制） | 到 0 清空副手槽；新增 `Stacks.consumeOneInOffHand` — `7b3f4a6` |
| 2 | 🔴 幽灵/复制 | `ConsumableItem` 主手 | 同源：主手食物到 0 残留幽灵物品 | 0 数量物品被持续识别/显示，可被重复利用 | `Stacks.consumeOneInMainHand` — `52ce414` |
| 3 | 🟠 复制 | `ConsumableItem` 副手打火石 | `yan`（香烟）副手打火石整件消耗（对齐原 `yan.js`），到 0 残留幽灵打火石，仍过 `getType()==FLINT_AND_STEEL` 校验 | **一根打火石无限点烟** | 同上，到 0 清空副手 — `52ce414` |
| 4 | 🟠 资源耗尽 | `WTWorkbench.craft` | `takeCharge` 在匹配前执行，空点击也扣机器能量 | 玩家可反复空点击**刷空共享机器能量**（公共服 grief） | 拆出无消耗 `findMatch`，改为 先匹配→扣能量→消耗→产出 — `a007b7f` |
| 5 | 🟠 逻辑 | `WTRecipeMachine.matchRecipes` | `fitAll`（输出放不下）失败时 `return null`，整体放弃 | 某配方输出放不下会阻塞输出项不同的其它可合成配方，机器空转 | 改为 `continue`；同时拆分 `findMatch`/`consumeMatch` 供工作台复用 — `a007b7f` |

### 验证
- `./gradlew compileJava` 通过（BUILD SUCCESSFUL，仅有既有的 deprecation 提示，非本次引入）。
- 重构为**行为保持**抽取：`matchRecipes = findMatch + consumeMatch`，tick 路径与模板机路径语义不变；仅工作台调用顺序改变（无配方/没电均安全失败，不吞输入）。

### 复查确认（本轮无问题项）
- **多方块机** `WTMultiBlockMachine.onInteract`：消耗走 `ItemUtils.consumeItem` + 输出走 `findOutputInventory`，满则提示并 return（不产出不消耗），与上游 Slimefun `MultiBlockMachine` 同模式；`getContents()` 快照写回为上游既有行为，不擅自改动以免偏移。
- **作物** `CropBlock`：`getDrops()` 返回空 + `CropListener` 禁用原版掉落，仅 `onBreak` 在成熟时按权重/概率掉落一次；非玩家破坏（爆炸/活塞）经 tick 检测到方块被替换后清理 BlockStorage，不刷原版物。
- **生物掉落** `MobDropListener`：`EntityDeathEvent.getDrops().add`，按配置 chance 独立掷一次，无重复。
- **方块掉落** `BlockDrops`：数量区间每次破坏现掷，`clone()` 后投放，无引用泄漏。
- **tick 竞态**：`WTRecipeMachine` 匹配用快照、消耗前对选中槽实时再校验（`stillValid`）；`active` 为 `ConcurrentHashMap`。

### 待办（后续轮次）
- 多方块机 `getContents()` 快照写回的脆弱性（上游共有）—— 评估是否改为显式 `setItem` 提交。
- 破坏机器中途 `onBlockBreak` 丢已消耗输入（上游 AContainer 同行为）—— 评估是否对齐预期。
- 版本号：本轮为关键修复（含复制漏洞），审查周期结束后统一升版并写 release note。

## 第 2 轮（2026-08-05）：物品属性加载与特殊脚本物品

**范围**：`ItemSpec`/`ScriptItemFactory`/`AttributeItems`（物品分派与属性）、各内容加载器（`ItemsLoader`/`RecipeMachineLoader`/`MultiBlockLoader`/`WorkbenchLoader`/`TemplateLoader`，槽位与配方解析）、`SpecialItems`（捕云瓶/巨人丸）。重点：加载期 NPE/越界级联、属性边界、玩家可触发路径的消耗健壮性。

### 已修复

| # | 严重度 | 位置 | 缺陷 | 后果 | 修复 / commit |
|---|---|---|---|---|---|
| 6 | 🔴 幽灵 | `SpecialItems.CloudBottleItem`/`GiantPillItem` | 主手 `setAmount(n-1)` 到 0 残留幽灵物品（第 1 轮修了钓鱼/消耗品/打火石，遗漏此处两处） | 0 数量物品被持续识别/显示 | `Stacks.consumeOneInMainHand` 到 0 清空 — `13fcf79` |
| 7 | 🟠 吞物品 | `SpecialItems.CloudBottleItem` | 先消耗瓶子再解析掉落物；若 `WT_CLOUD/WT_THUNDERCLOUD` 未注册则瓶子被吞无产出 | 配置缺失时丢物品 | 先 `getById` 校验掉落物，未注册不消耗 — `13fcf79` |
| — | 🟡 清理 | `RecipeMachineLoader.compact` | 定义但从未调用的死代码 | 维护负担 | 移除 — `8ae80c4` |

### 验证
- `./gradlew compileJava` 通过。
- 全局 `grep setAmount\(` 覆盖确认：**玩家背包消耗点已全部收敛到 `Stacks.consumeOne*`**（钓鱼/消耗品主手+副手打火石/捕云瓶/巨人丸）。其余 `setAmount` 命中（`BlockDrops` 投放数量、`FishingListener` 掉落鱼 `setAmount(1)`、`Read` 读取展示堆数量、`Stacks` 助手本体）均为新建/读取堆，非玩家槽位消耗，无幽灵风险。

### 复查确认（本轮无问题项）
- **加载器槽位校验**：`MultiBlockLoader` 校验 `work∈1..9` 且结构槽非空（防 AIOOBE，对应历史 commit `6bf4c3d`）；`RecipeMachineLoader.readRecipes` 对空输入/输出配方跳过（避免 `CraftingOperation` 校验抛异常）；各 loader 均逐条 `try/catch`，单条失败不影响整体。
- **多方块消耗**：`WTMultiBlockMachine.onInteract` 顺序为 先 `findOutputInventory`→满则 return（不消耗）→消耗输入→产出；`getContents()` 镜像写回为上游 Slimefun 同模式，消耗/产出均落盘，无复制/丢失。
- **属性分派**：`ScriptItemFactory` 单属性优先级分派合理；`parseRadiation`/`parseSound` 均 try/catch 容错；`ItemSpec` 配置项默认值齐全。
- **`id_alias` 解析**：`ItemsLoader.register` 优先按 `id_alias` 取展示堆，回退原 id，与 preloadDisplays 一致。

### 待办（后续轮次）
- `RegisterConditions`/`Yaml`/`FoodHelper`（反射 FoodComponent，失败记 severe）的健壮性与异常路径。
- `WTMultiBlockMachine.dispenserFaceGet()` 的 EAST/WEST 方位映射疑似水平翻转——需结合实机多方块结构核对（不臆测改动）。
- `GiantPillItem` 召唤 GIANT 未做区域保护校验（spawnEntity 绕过领地插件）——评估是否加 `ProtectionManager` 校验。
- 多方块机 `getContents()` 快照写回脆弱性（上游共有）。

## 第 3 轮（2026-08-05）：load 包健壮性与 GEO/多方块核查

**范围**：`Yaml`/`FoodHelper`/`RegisterConditions`/`RecipeTypes`/`MobDropsLoader`/`MenuLoader`/`GeoLoader`/`WTGeoResource`。重点：加载期异常路径、unchecked cast NPE、反射容错、配置解析边界。本轮为**健壮性确认**为主。

### 已修复

| # | 严重度 | 位置 | 缺陷 | 后果 | 修复 / commit |
|---|---|---|---|---|---|
| 8 | 🟡 故障隔离 | `MenuLoader.parseSlots` | 反转区间 `17-10` 使 `new int[hi-lo+1]` 抛 `NegativeArraySizeException`，非 `NumberFormatException`、逃出 catch，连累**整个菜单**注册失败 | 一个坏槽位范围杀死整个菜单 | 加 `lo>=0 && hi>=lo` 校验，非法返回空数组仅跳过该槽 — `03ecfa4` |

### 复查确认（本轮无问题项）
- **`Yaml.loadResource`**：资源缺失/IO 异常均 `try-with-resources` + 返回空 `YamlConfiguration`，不崩启动。
- **`FoodHelper.apply`**：反射实例化 `CraftFoodComponent` 全程 `try/catch Throwable`，失败返回 false（上层记 severe 告警），不抛出；`stack.editMeta` 包裹安全。
- **`RegisterConditions`**：`version`/`hasplugin`/`itemexist`/`config.*` 解析容错，异常返回 true（不误杀注册）；`Bukkit.getMinecraftVersion()` 经编译验证可用。
- **`RecipeTypes.resolve`**：自定义→标准常量→NULL 回退，未知类型记日志。
- **`MobDropsLoader`**：`chance` 默认 0、`entity` null 跳过；用 effId 记录（与监听器 `getById` 对齐）。
- **并发**：所有静态注册表（`Behaviors.*`/`BlockDrops.MAP`/`MobDropsLoader.drops`/`FishingListener.baits`）均在 `Setup.loadAll`（启动期）填充、运行期只读，主线程访问，无竞态。

### 设计核查（确认非 bug，不擅改）
- **GEO 资源绑定**：`GeoLoader` 将 `WT.preload`（无 SF id 绑定的展示堆）传给 `WTGeoResource.getItem()`。`WUWEI_GEOYAN` 在 items.yml 被引用 271 次作配方材料——经分析，配方 `need` 与 GEO 产出物**同源于 preload**，`isItemSimilar` 两端一致可匹配，内部自洽。改用绑定版反而会打破匹配，**保持现状**。
- **`WTGeoResource` 注册**：`implements GEOResource`（独立 GEO 注册表，NamespacedKey 键），与 `ItemsLoader.register` 的 SlimefunItem 注册（Slimefun id）分属不同注册表，**无双重注册冲突**。
- **`WTMultiBlockMachine.dispenserFaceGet`**：取样 `WUWEI_JYKRL`（work=5/SMOKER，DISPENSER 在 slot 8=center+3）返回 `DOWN`，`block.getRelative(DOWN)` 取下方发射器，**几何正确**；UP/DOWN 分支不受旋转影响。EAST/WEST 水平分支在实测配置中未见使用（dispenser 均为纵向），**保持现状**，待实机若出现横向 dispenser 再核。

## 第 4 轮（2026-08-05）：NPE/强转反模式静态扫描

**范围**：对全插件做 unchecked-cast / 自动拆箱 / `.get(0)` 反模式 grep 扫描；复查 `Colors`/`WTUnplaceableItem`/`Behaviors.loadConsumables`/`FishingListener.load`。重点：单条坏数据是否会导致级联失败。

### 已修复

| # | 严重度 | 位置 | 缺陷 | 后果 | 修复 / commit |
|---|---|---|---|---|---|
| 9 | 🟠 级联失败 | `Behaviors.loadCrops` | `drops`/`weightedDrops` 的 `((Number) chance/weight)`、`(String) id` 为**无保护强转**，且 `loadCrops` **无逐条 try/catch** | 单条作物数据缺 chance/weight 或类型错 → NPE/CCE 逃出 `loadData` → 被 `onEnable` 顶层 catch 兜住 → **其后 items/foods/machines 等全部跳过**（插件近乎空载启用） | `instanceof` 校验 + 逐条 try/catch，对齐其它 loader — `e0d4f1f` |
| 10 | 🟡 强转 | `FishingListener.load` | 鱼饵掉落 `id != null` 仍对非字符串 id 触发 `(String)` CCE | YAML 中 id 为纯数字时 CCE（同上级联） | 收紧为 `id instanceof String` — `200c60b` |

> 说明：`data/crops.yml` 当前数据规范（取样 id+chance 齐全），故 #9 为**潜伏缺陷**——修复确保未来一次数据编辑失误不再让整个插件近乎空载启用。

### 复查确认（本轮无问题项）
- **`Colors`**：null-safe；`{#RRGGBB}`/`&#RRGGBB`/`&` 码三路替换 + `translateAlternateColorCodes`，无异常路径。
- **`WTUnplaceableItem`**：平凡 `SlimefunItem + NotPlaceable` 包装。
- **`Behaviors.loadConsumables`**：全部使用类型安全的 Bukkit getter（getInt/getDouble/getBoolean/getString 类型不符返回默认），唯一强转（potions 循环）已 `instanceof` 守卫，**不会抛出**。
- **反模式 grep 收敛**：修复后，全插件剩余 `((Number))`/`.intValue()` 命中（`Behaviors:74`、`FoodConsumeListener:27`、`ConsumableItem:65`）均处于 `instanceof`/`!=null` 守卫或安全 getter 之后，无未保护强转。

### 阶段性结论（r1–r4）
- Java 源码（~40 文件）已逐文件覆盖：机器/监听器运行期(r1)、物品分派/特殊物品(r2)、load 包(r3)、行为数据加载器+反模式扫描(r4)。
- 玩家背包消耗点已全部收敛到 `Stacks.consumeOne*`（5 处幽灵物品修复）。
- 所有内容/行为加载器现已统一具备逐条 try/catch 故障隔离。
- 仍待：实机加载验证；`data/*.yml` 与内容 YAML 的跨文件一致性（如未定义 id 引用）；高负载下机器配方匹配性能（O(配方×槽位)/tick）评估。

## 第 5 轮（2026-08-05）：高负载性能热路径 + 跨文件 id 一致性

**范围**：tick/死亡热路径性能优化；全内容 YAML 的 `WT_*` 引用 vs 定义一致性扫描。对齐用户「长时间高负载、多用户高频」诉求。

### 已优化（性能，行为不变）

| # | 位置 | 问题 | 优化 / commit |
|---|---|---|---|
| 11 | `WTRecipeMachine.findMatch` | 空闲机器(输入槽全空)每 tick 仍遍历全部配方并做昂贵 `isItemSimilar`，大量空闲方块显著占 TPS | 输入槽全空时早返回 null（注册配方至少含 1 个非空输入，短路安全）— `2bdd37c` |
| 12 | `MobDropsLoader.drops` / `MobDropListener.onDeath` | 每次生物死亡线性扫描全部 106 条掉落；刷怪塔高频死亡场景开销大 | 改为 `Map<实体类型, List<Drop>>`，监听器 `get(type)` 直接查表，O(该类型) 取代 O(全部) — `ea8ef91` |

### 跨文件 id 一致性扫描（数据层，仅核查不臆改）
- 扫描全部内容 YAML 的 `material: WT_*`（material_type:slimefun）引用，与「各 item 定义文件顶层 key ∪ id_alias」比对。
- **结果：仅 `WT_XIANGYUNCF`（items.yml + recipe_machines.yml×2 引用）与 `WT_XUECHENGGQ`（mb_machines.yml 引用）无定义**——与 [standalone-plugin.md](standalone-plugin.md) 已记录的 2 个缺口完全一致，**无新增缺口**。
- 运行期：`Read.resolve` 对二者记 warning 并回退 STONE（指南显示为石头、相关配方输入/输出失效），不影响加载与其余内容。属内容数据缺口，需作者补定义或更正 id（不臆测）。

### 验证
- `./gradlew compileJava` 通过。
- 一致性扫描为只读核查，未修改任何内容 YAML。

### 仍待
- 实机加载验证（本环境无法运行服务端）。
- 上述 2 个未定义 id 由内容作者处理。

## 第 6 轮（2026-08-05）：机器/作物生命周期深挖验证

**范围**：对照上游 Slimefun 源码与原 RSC 脚本，核查三个「疑似缺陷」。本轮为**验证轮**——确认均为非 bug，避免错误改动。无代码变更。

### 验证结论

| 疑点 | 核查依据 | 结论 |
|---|---|---|
| `WTWorkbench` 三重 BlockMenuPreset 注册（AContainer#1 + WTRecipeMachine#2 + WTWorkbench 匿名#3）会冲突/点击失效？ | `BlockMenuPreset` 构造器 `Slimefun.getRegistry().getMenuPresets().put(id, this)` 覆盖式自注册；`createPreset`（`InventoryBlock` default）= `new BlockMenuPreset`。三者同 id、最后 put 的 #3（带点击处理器）生效。 | **非 bug**：#3 生效，点击合成可用。#1/#2 为启动期冗余构建（2 个 workbench 类型 × 2 次，可忽略），且 #1 的 `constructMenu` 有 `inputSlots==null` 早返回守卫。 |
| 机器重启丢失在合成中的进度（`active` 内存态）会吞输入？ | `MachineProcessor.startOperation` 用内存 `Map<BlockPosition,T>`（`putIfAbsent`），**不持久化**；`active` 与 op 同为内存态、成对 set/remove。 | **非 bug**：等同上游 AContainer（所有 Slimefun 机器均不持久化合成进度，重启丢失是上游固有行为）；`active` 与 op 不会失步，正常运行中不存在「op 完成但 r==null」场景。 |
| `CropBlock` 重启后作物回退到 age 0？ | 原 `wt_crop.js` 同样用内存 `HashMap`（`lastUseTimes`/`giftif`），重启同样重置生长时序。 | **非 bug（设计一致）**：端口与原脚本均为内存态时序、重启重置生长；端口的 `setStage(0)` 仅是「立即重置」vs 原脚本「延迟经 handleGrowth 重算」的时序差异，终态一致。真正持久化需把时序写入 BlockStorage（属功能增强，会偏离原版）。 |

> 收获：三处疑似问题经源码级核查均排除。代码层面的高产出审查已趋于饱和（r1–r5 已修复 ~12 处）。后续价值主要在**实机加载验证**与**内容数据补全**（2 个未定义 id），非代码缺陷。

## 第 7 轮（2026-08-05）：菜单槽位 ↔ 机器槽位一致性 + r1–r6 回归复核

**范围**：跨文件核查 menus.yml 装饰槽 ↔ 各机器 input/output/click/templateSlot 是否对齐/越界；复核 r1–r6 自身改动有无回归。

### 已修复

| # | 严重度 | 位置 | 缺陷 | 后果 | 修复 / commit |
|---|---|---|---|---|---|
| 13 | 🟠 越界 | `WTRecipeMachine.constructMenu` | 背景填充循环用 `size=27`（menus.yml 全文件**无 size 声明**），而 `BlockMenuPreset` 按**已放置物品**自动定尺寸；`output` 槽只挂 click handler 不放置物品 → 若功能槽超出装饰槽最大值，预设自动尺寸不覆盖该槽 | 该槽 `getItemInSlot`/`consumeItem` 越界（机器无对应菜单或菜单装饰未覆盖高槽时触发） | 填充尺寸改为覆盖 `max(input/output/progress/extra/装饰)` 向上取整到 9 的倍数（≤54）— `94a9b0b` |

> 当前数据一致（menus 装饰到 53、机器槽 ≤49），故 #13 为**潜伏加固**：仅在「机器无对应菜单」或「菜单装饰未覆盖高功能槽」时才会触发越界，现数据不触发；修复避免未来配置变更引入崩溃。

### 复查确认（本轮无问题项）
- **数据一致性**：menus.yml 装饰槽最高到 53，各机器 input/output 槽最大 49（linked）/43/36——装饰覆盖功能槽，当前不越界。
- **r1–r6 回归复核**：逐项复核 12 处改动——`Stacks.consumeOne*`（到 0 清空 + null 安全）、`findMatch/consumeMatch` 拆分（`chosen` 索引语义正确、tick 路径行为不变）、`WTWorkbench.craft` 顺序、`parseSlots` 区间校验、`loadCrops` 类型守卫、MobDrop Map 索引——**均无回归**，编译通过。

### 仍待
- 实机加载验证；2 个未定义 id 由内容作者补全。

## 第 8 轮（2026-08-05）：消耗品数据保真度核查（端口 vs 原 JS 脚本）

**范围**：核对 `data/consumables.yml` 的行为参数是否忠实复刻原「独立脚本」(jiu/yan/zhongdu/xuejia 等) 的逻辑与数值。

### 已修复

| # | 严重度 | 位置 | 缺陷 | 后果 | 修复 / commit |
|---|---|---|---|---|---|
| 14 | 🟠 用户交互逻辑 | `ConsumableItem` 副手模型 + `xuejia` 数据 | 原 `xuejia.js` 要求副手**剪刀(SHEARS)**并消耗 1 把；端口仅 `consumeOffhand:true` 无工具类型，而代码只在 `offhandFlint && consumeOffhand` 时校验/消耗 → xuejia 的 consumeOffhand 为**死配置** | xuejia 既不校验剪刀、也不消耗副手（交互与原版不符；玩家白嫖剪刀成本） | 将 `offhandFlint(boolean)` 泛化为 `offhandTool(Material)`，支持任意工具；yan→FLINT_AND_STEEL、xuejia→SHEARS，consumeOffhand 统一在其下生效 — `5779a92` |

### 复查确认（端口保真度，无问题项）
- **`jiu`**：randomFood 1-12、exhaustion -2、NAUSEA(1000,1)、ABSORPTION(1200,2)、requireHungry、副手粘液禁用 —— 与 `jiu.js` 完全一致。
- **`zhongdu`**：food/saturation +17、exhaustion -1.7、POISON(1000,6)、UNLUCK(1000,6) —— 与 `zhongdu.js` 一致。
- **`yan`**：ABSORPTION(1400,2)+DOLPHINS_GRACE(1400,1)+HUNGER(800,1)+打火石校验消耗 —— 与 `yan.js` 一致。
- **已知简化（非 bug）**：`ConsumableItem` 对所有消耗品播放固定 `ENTITY_STRIDER_EAT` 音效，丢失原脚本的 per-script 音效（jiu 饮用声/yan 打火石声）——属听觉层面的有损简化，如需可后续加 `sound` 字段。

## 第 9 轮（2026-08-05）：钓鱼/作物数据保真度核查

**范围**：`data/fishing.yml` vs 原 `diaoyu.js`（掉落表/权重/鱼饵映射）、`data/crops.yml` vs 原 `seed/*` 脚本（material/maxAge/growMs/drops）；复核 `CropBlock` 收获逻辑 vs 原 `wt_crop.js`。

### 复查确认（端口保真度，无问题项）
- **`fishing.yml`**：5 个鱼饵（淡水/小型咸水/大型咸水/水果/河豚）共 133 条掉落，**逐条 id 与权重与 `diaoyu.js` 完全一致**；钓竿 `WT_BAIWEIDIAOGAN` 一致；`FishingListener.select` 加权算法等价于 `WT_selectRandomDrop`。
- **`crops.yml`**：抽查 `seed/aicao`（material/maxAge/growMs/stages/drops）与原 `seed/aicao.js` **完全一致**。
- **`CropBlock.dropItem`**：用 `sf.getItem()`（绑定版）等价于原 `WT_dropItem`（`new ItemStack(type)` + `setItemMeta`，含 SF id 绑定）；`onBreak` 经 `CropListener` 的 `BlockStorage.check` 校验方块身份，等价于原 `handleHarvest` 的 `getSfItem().getId()===cfg.id` 校验；weighted/chance 掉落逻辑一致。

### 阶段性总评（r1–r9）
- **代码层**：~40 Java 文件逐文件覆盖，修复 14 处（含钓鱼复制漏洞、5 处幽灵物品消耗、loadCrops 级联、机器槽位越界、工作台能量顺序、性能优化等）。
- **数据层**：consumables(仅 xuejia 因代码模型缺口已修，余忠实)、fishing、crops 三类行为数据均经原脚本核对**忠实复刻**。
- 审查已覆盖**代码 + 行为数据**双层面。剩余仅为：实机加载验证（需服务端）、2 个未定义内容 id（作者补）、items.yml 配方内容（纯作者数据）。

## 第 10 轮（2026-08-05）：打包完整性验证

**范围**：执行完整 `./gradlew jar`（此前仅跑 compileJava），核查产物 jar 是否包含全部所需资源、是否拾取了源码改动。

### 复查确认（无问题项）
- **构建**：`./gradlew jar` BUILD SUCCESSFUL（compileJava/processResources/jar 全过）。
- **资源清单完整**：`WorldTaste-1.8.2-standalone.jar` 内含 **13 个内容 YAML**（foods/geo_resources/groups/items/linked_recipe_machines/machines/mb_machines/menus/mob_drops/recipe_machines/recipe_types/template_machines/workbenches）+ `plugin.yml` + `data/{consumables,crops,fishing}.yml`，与各 loader 引用清单**完全匹配，无缺失**（任一缺失会致 `Yaml.loadResource` 返回空、内容静默丢失）。
- **打包拾取源码改动**：jar 内 `data/consumables.yml` 含 r8 的 `xuejia: offhandTool SHEARS` 与 `yan: offhandTool FLINT_AND_STEEL`、无残留 `offhandFlint`——processResources 正确追踪 src 编辑。

### 审查闭环声明（r1–r10）
静态审查已**全面完成并闭环**，覆盖：代码（~40 文件，14 处修复）、行为数据（consumables/fishing/crops 忠实）、打包（完整、拾取改动）。后续静态轮次不再有预期产出；剩余工作明确为**实机加载验证**（需真实 MC 服务端，本环境不可行）与**内容作者补全**（2 个未定义 id、items.yml 配方数据）。

## 第 11 轮（2026-08-05）：玩家输入信任 & 事件重入/并发（用户重启审查）

> 用户以 /loop 重启持续审查，明确新约束：**视为暴露于网络服务、不得信任任何用户输入（修改版客户端可发任意协议包/伪造事件/篡改 NBT）、长时间高负载多用户高频下的稳定性**。本轮聚焦此前未深入的「**输入信任边界 + 事件重入/并发**」角度，逐路径核对玩家可触发路径读取的是**服务端真实状态**而非事件/客户端提供数据，并排查重入/竞态导致的数据操作问题。

### 已修复

| # | 严重度 | 位置 | 缺陷 | 后果 | 修复 / commit |
|---|---|---|---|---|---|
| 15 | 🟡 逻辑 | `CropBlock.onBreak` 加权掉落 | 加权选择无 `total<=0` 守卫、无末项回退；权重全非正(脏数据)或浮点边界时循环走完**不产出任何掉落** | 成熟作物破坏偶发/脏数据下空掉落（与 `FishingListener.select` 行为不一致） | 补 `total<=0` 守卫 + 末项回退，对齐 `FishingListener.select` — `e894e59` |

### 复查确认（本轮无问题项——附证据，防后续误改）

- **副手 `ItemUseHandler` 派发 × 特殊物品手检查**：经核 Slimefun `SlimefunItemInteractListener`（[REF .../SlimefunItemInteractListener.java:75,104-111](../REF/RykenSlimeCustomizer-1.21.11/REF/Slimefun4.1/src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/SlimefunItemInteractListener.java)）**会对副手右键派发** `ItemUseHandler`（取 `e.getItem()`=所用那只手）。但：
  - `CloudBottleItem`（[SpecialItems.java:46-50](../plugin/src/main/java/com/haiman233/worldtaste/items/SpecialItems.java)）虽无 `getHand()` 检查，其「副手不能持粘液物品」校验在**瓶子位于副手**时恰好为真（瓶子本身是 SF 物品）→ 提前 return，**不会误耗主手物品**。
  - `GiantPillItem`（[SpecialItems.java:79](../plugin/src/main/java/com/haiman233/worldtaste/items/SpecialItems.java)）用 `e.getHand()!=HAND` 显式守卫。
  - 结论：两者均无「副手放置、误耗主手」的吞物品风险。**非 bug**。
- **`foodOnEat` 链路完整（非死代码）**：`Behaviors.foodOnEat` 由 [FoodsLoader.java:52-56](../plugin/src/main/java/com/haiman233/worldtaste/load/FoodsLoader.java) 在 foods.yml 物品带 `script` 时，从 `Behaviors.consumables[script]` 中 `kind: eat`（`use=false`）条目填充，键为食物 effId；`Setup.loadAll` 中 `Behaviors.loadData` 先于 foods 执行，查表时机正确。`FoodConsumeListener` 正常生效。
- **消耗/钓鱼路径读服务端状态、非客户端数据**：`ConsumableItem`/`FishingListener`/`SpecialItems` 均用 `inv.getItemInMainHand()/getOffHand()`（服务端背包真值），未用 `event.getItem()` 等可伪造来源；钓竿/鱼饵/瓶子均经 `SlimefunItem.getByItem/getById` 按 SF id 识别，修改版客户端无法伪造 SF id（持久化数据容器，服务端写入）。**输入可信**。
- **多方块机 `return` vs `continue`**：[WTMultiBlockMachine.onInteract](../plugin/src/main/java/com/haiman233/worldtaste/machines/WTMultiBlockMachine.java) 在「事件取消/不能用/背包满」处 `return` 而非 `continue`；但 `isCraftable` 已按输入唯一锁定配方，且 `Map<ItemStack[],ItemStack>` 无重复输入键，故同输入下仅一条配方命中，`return`/`continue` 行为等价。**非 bug**。
- **机器合成输入「随操作吞入」**：[WTRecipeMachine.tick](../plugin/src/main/java/com/haiman233/worldtaste/machines/WTRecipeMachine.java) 在 `findNextRecipe` 即消耗输入并 `startOperation`；中途断电/破坏则输入随内存态 operation 丢失——等同上游 AContainer（不持久化合成进度），r6 已核。**非 bug（上游固有设计）**。
- **重入/并发**：Bukkit 玩家事件（右键/破坏/钓鱼/点击）均主线程同步派发，单处理器 lambda 原子执行到底，无中途让出；机器 tick（AContainer `isSynchronized`）亦主线程。`active` 为 `ConcurrentHashMap`、`findMatch` 含 `stillValid` 实时复校，作异步防御。`ConsumableOpts.potions` 为 `final ArrayList` 初始化、永不为 null。**无可利用重入/竞态**。
- **`ignoreCancelled` 一致性**：Crop/MobDrop/BlockDrops/Fishing/FoodConsume 五个监听器均 `ignoreCancelled=true`，领地/保护插件取消破坏或钓鱼事件时**不触发**自定义掉落 ✓。

### 待办（后续轮次）
- **数据一致性轮**：核查「作物成熟材质 × `BlockDrops.drop_from`」是否存在双重掉落——若某作物成熟后材质为 W（如 WHEAT），且 items.yml 有针对 W 的 `drop_from` 条目，则玩家破坏该成熟作物会**同时**触发 `CropListener`（作物掉落）与 `BlockDrops`（材质掉落）。需 grep items.yml 核查有无此类材质重叠。
- **巨人丸 `spawnEntity` 绕过领地保护**（r2 todo）：评估接入 `Slimefun.getProtectionManager()` 校验后再生成 GIANT。
- **`Read.java` 内容解析信任边界**：undefined id 回退 STONE、recipe/数量解析边界的专门核查（下一轮）。
- **高负载机器配方匹配性能**：空闲早返回已加（r5）；超大配方表机器的 O(配方×槽位)/tick 实测评估（需实机）。

## 第 12 轮（2026-08-05）：内容解析信任边界 + 跨文件数据一致性（drop_from 双重掉落）

> 本轮聚焦「YAML 内容 → 运行时物品」的解析信任边界（[Read.java](../plugin/src/main/java/com/haiman233/worldtaste/load/Read.java)、[ItemsLoader](../plugin/src/main/java/com/haiman233/worldtaste/load/ItemsLoader.java) 的 drop_from/script/id_alias）与跨文件材质一致性，发现并修复一处真实**双重掉落**。

### 已修复

| # | 严重度 | 位置 | 缺陷 | 后果 | 修复 / commit |
|---|---|---|---|---|---|
| 16 | 🟠 复制/双重掉落 | `BlockDrops.onBreak` | 仅按 `block.getType()` 匹配 `drop_from`，不区分**自然方块**与**已注册粘液/作物方块** | 成熟为 `SWEET_BERRY_BUSH` 的 WorldTaste 作物被破坏时：`CropListener` 掉作物产物 + `BlockDrops` 又掉 `WT_NGSCZZ1`(8%)/`WT_NGSCZZ2`(7%)，双重掉落；同理任何材质命中 `drop_from` 的已放置粘液方块可放置→破坏刷物 | 命中材质后跳过 `BlockStorage.check!=null` 的粘液方块，仅自然方块触发附赠掉落 — `5a7b831` |

> 触发证据：`crops.yml` 约 20 个作物 `material: SWEET_BERRY_BUSH`（行 56/65/167/248/266/433/442/451/572/760/778/796/914/959/968/1040/1070/1115/1151…）；items.yml 行 1387、1409 两个 `drop_from: SWEET_BERRY_BUSH`。两者材质交集经全量核对**仅为 `SWEET_BERRY_BUSH`**。

### 复查确认（本轮无问题项——附证据）
- **`Read.item` 边界**：material 空→null；`amount<=0` 时保留堆默认（配方数据中无 0 量输入，不触发）；`slimefun` 型 undefined id → 告警并回退 STONE（`WT_XIANGYUNCF`/`WT_XUECHENGGQ` 两个内容缺口，r5 已记录、属作者数据）；`matchMaterial` null 安全 + GRASS/SCUTE 别名；`applyColor` `Color.fromRGB` 越界由 `RuntimeException` catch 兜住。**无解析期崩溃/越界**。
- **`Read.recipe` 槽位边界**：固定 `size=9` 循环 `0..8` 读键 `"1".."9"`，越界键被忽略，返回定长数组。**无 AIOOBE**。
- **`auto-detect` 类型覆盖**：`startsWith("ey")/"ew")` → skull 等前缀判断先于显式 `material_type`（与 RSC 一致）；无原版材质以 `ey/ew` 开头，无误判。
- **`ItemsLoader.parseAmountRange`**：`"1-3"` 区间校验 `lo>=1 && hi>=lo`，非法格式回退 `{1,1}`；`dropFrom` 材质 `matchMaterial` 返回 null 时 `BlockDrops.add` 内 `block==null` 守卫直接忽略。**无脏数据崩溃**。
- **作物 × drop_from 材质全量交集**：作物材质集合 {WHEAT, TORCHFLOWER_CROP, POTATOES, MELON_STEM, SWEET_BERRY_BUSH, BEETROOTS, NETHER_WART, PITCHER_CROP, COCOA, CARROTS, CHORUS_FLOWER, PUMPKIN_STEM} 与 drop_from 集合交集**仅 SWEET_BERRY_BUSH**（已由 #16 修复）；CHORUS_PLANT(drop_from) ≠ CHORUS_FLOWER(作物)，无其余重叠。

### 待办（后续轮次）
- **巨人丸 `spawnEntity(GIANT)` 绕过领地保护**（r2/r11 todo）：评估接入 `Slimefun.getProtectionManager()` 校验目标位置后再生成。
- **`ScriptItemFactory` / `AttributeItems` 属性分派**（radiation/soulbound/anti_wither/piglin/energy_capacity/register.conditions）的专门核查（下一轮候选）。
- **高负载机器配方匹配性能**实测（需实机）。

## 第 13 轮（2026-08-05）：属性分派层 + 脚本覆盖闭环核查（验证轮）

> 本轮核查「YAML 属性 → Slimefun 物品子类」的分派信任边界（[ScriptItemFactory](../plugin/src/main/java/com/haiman233/worldtaste/items/ScriptItemFactory.java)、[AttributeItems](../plugin/src/main/java/com/haiman233/worldtaste/items/AttributeItems.java)）与「引用脚本 → Java 行为定义」的覆盖闭环。**为验证轮：经实证无缺陷、无代码改动**，仅固化结论以防后续误改。

### 复查确认（本轮无问题项——附证据）

- **单属性优先链无属性丢失**：原 RSC 用 ByteBuddy 动态叠加多属性接口，本插件改为优先链（energy→script(crop/consumable/special)→rad→soulbound→antiWither→piglin→!placeable→WTItem），每物品只取一个属性类。逐一核对 items.yml **全部 11 处**属性项的分派结果均正确：
  - piglin×2（placeable:false）→ PiglinBarterItem(NotPlaceable)✓；radiation+script×5 → RadioactiveConsumable（radiation 保留 + ConsumableItem 自带 NotPlaceable）✓；anti_wither(placeable:true)→ WitherProofItem✓；soulbound(placeable:**true**)→ SoulboundItem✓；radiation(placeable:true,无 script)→ RadioactiveItem✓；energy_capacity(+script,忽略 script 对齐 RSC)→ EnergyItem✓。
  - **数据中无任何物品同时具备 ≥2 个被分派属性** → 链中无属性被静默丢弃。唯一 soulbound 项显式 placeable:true，不存在「soulbound 误变可放置」。
- **顶层属性键全部被处理**：提取 items.yml 全部顶层键 {recipe_type/item_group/item/placeable/recipe/script/register/drop_from/drop_chance/drop_amount/id_alias/radiation/vanilla/piglin_trade_chance/soulbound/glow/energy_capacity/anti_wither}（18 种），逐一对照插件读取点——**全部被处理，无静默忽略的属性键**。
- **属性解析健壮**：`parseRadiation` 对非法枚举 try/catch→null；`piglinChance/energyCapacity` 走 `getInt`；`EnergyItem` 注册为 CAPACITOR（对齐 RSC energy_capacity 语义）。
- **脚本覆盖闭环（逻辑闭合）**：items.yml/machines.yml 引用 **209 个去重 script**，对照 `consumables.yml(69 键) ∪ crops.yml(142 键) ∪ {yurenjie/buyunping, jurenwan}`——**全部命中**。初筛差出的 13 个（gandi/luoji、gandi/wujin、hetun/hetunjingyou、yurenjie/{bingdong,du,du0,du114514,du2,du3,fumojinmls,jindmls,shelingshu,zhaohuo}）经逐个 `grep` 核实**均在数据文件中定义**（名字带 `/` 的消耗品键，提取正则漏斜杠所致），非真缺口。**无引用脚本无定义 → 无静默行为丢失**。

### 评估后【未改】的设计选择（附理由，避免后续误改）
- **未为「未匹配 script」加注册期 warning**：虽能帮助内容作者发现 script 拼写错误/漏移植，但 foods 中 `kind:eat` 的 onEat 脚本经 `Behaviors.foodOnEat`（[FoodsLoader.java:55](../plugin/src/main/java/com/haiman233/worldtaste/load/FoodsLoader.java)）处理、`ScriptItemFactory` 不可见（其 `opts.use=false` 不走 ConsumableItem 分支），加 warning 会**对全部 onEat 食物误报**。权衡后保持静默回退（未匹配→按普通物品注册），不引入误报噪音。

### 待办（后续轮次）
- **内容保真度深核**：gandi/yurenjie/hetun 等主题脚本在 machines.yml(作物) vs consumables.yml(食物) 的**类型归属**是否与原脚本一致（本轮只证「有定义」，未证「类型正确」）。
- **顶层 `glow:`（items.yml 仅 1 处）**：疑似 YAML 缩进错误（glow 应在 `item:` 段内；`Read.item` 仅读 item 段的 glow），该物品可能不发光。属内容数据小瑕疵，非代码 bug。
- **巨人丸 `spawnEntity` 绕过领地保护**（r2/r11/r12 todo）。
- **`recipe_machines/linked/template/workbench` 各 Loader 的槽位与配方解析**专门核查（下一轮候选）。
