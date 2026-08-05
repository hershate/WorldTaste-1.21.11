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
