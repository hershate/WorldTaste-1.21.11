# 独立 Slimefun4.1 插件改写说明

> 本文件记录把 WorldTaste 从「RSC 脚本 + GraalVM 脚本引擎」改写为**独立 Slimefun4.1 插件**的设计。
> 代码位于 [plugin/](../plugin/)，完全依照本地 [REF/RykenSlimeCustomizer-1.21.11/REF/Slimefun4.1](../REF/RykenSlimeCustomizer-1.21.11/REF/Slimefun4.1/) 源码（修改版，已适配 1.21.11），不依赖在线版本。

## 设计思路

原 WorldTaste 的内容全部在 YAML，行为在 JS 脚本（靠 RSC 的 GraalVM 引擎 eval）。改写为独立插件后：
- **内容 YAML 原样保留**（仍由插件在启动期读取），只是读取器从 RSC 的 Reader 换成插件自带的精简 loader。
- **JS 脚本逻辑全部以原生 Java 实现**；其“参数”（食物数值、作物配置、钓鱼掉落表）记录在 `plugin/src/main/resources/data/*.yml`，**手工维护**（不再使用任何转换脚本），运行期由 Java 读取。
- 结果是单个 `WorldTaste-1.8.2-standalone.jar`，**不再依赖 RykenSlimefunCustomizer**，放入 `plugins/` 即可（仍需 Slimefun + 美食家 + 异域花园）。

## 构建与依赖

- [plugin/build.gradle.kts](../plugin/build.gradle.kts)：`compileOnly` 本地 `REF/Slimefun4.1/target/SlimeFun4.1-4.9.5.jar` + Paper API 1.21.11。
- 构建：`cd plugin && ./gradlew build` → 产出 `plugin/build/libs/WorldTaste-1.8.2-standalone.jar`。
- `processResources` 把仓库根目录的内容 YAML（items/machines/...）一并打入 jar；`data/*.yml` 在 `src/main/resources/data/`。

## 代码结构（plugin/src/main/java/com/haiman233/worldtaste/）

| 包 | 职责 |
|---|---|
| `WorldTastePlugin` | 主类，`extends JavaPlugin implements SlimefunAddon`，`onEnable` 调 `Setup.loadAll()` |
| `load/` | 各内容加载器（见下）+ 公共读取器 `Read`/`Yaml`/`RecipeTypes` |
| `machines/` | `WTRecipeMachine`(AContainer)、`WTMultiBlockMachine`(MultiBlockMachine)、`WTRecipe`、`MenuDef` |
| `items/` | `ConsumableItem`、`CropBlock`、`ScriptItemFactory`、`SpecialItems` |
| `behavior/` | `Behaviors`(数据注册表)、`FishingListener`、`CropListener`、`MobDropListener` |
| `util/` | `Colors`、`Stacks` |

### 加载顺序（[Setup.loadAll](../plugin/src/main/java/com/haiman233/worldtaste/load/Setup.java)）

```
groups → recipe_types → 预加载展示物品 → Behaviors.loadData
→ items(+machines 脚本物) → foods → mob_drops
→ menus → recipe/linked/workbench 机器 → multiblock → template → geo
→ Behaviors.registerListeners
```

- **预加载展示物品**：先把各物品/机器的 `item:` 段建成 `WT.preload`，使后续配方能跨文件按 `material_type: slimefun` 引用（对齐 RSC preloadItems）。
- **每条注册均 try/catch**：单条异常不影响整体加载。

## 脚本行为移植对照

| 原脚本 | Java 实现 | 覆盖 |
|---|---|---|
| `lib/wt_food.js` + 各食物壳（编号 1~20、`tang_*`、`yl_*`、`jiu`、`yan`、`zhongdu*`、`gz*`、`rou`、`baohe`、`huifu`、`jianya`、`kangxing`、`maoxian`…） | `ConsumableItem`（读 `data/consumables.yml`） | 71 个脚本 |
| `lib/wt_crop.js` + `seed/*`(含 `seed/new/*`)、`gandi/*`、`yurenjie/*` 作物 | `CropBlock`（BlockTicker，读 `data/crops.yml`） | 142 个作物 |
| `diaoyu.js` + `lib/wt_fishing.js` | `FishingListener`（PlayerFishEvent，读 `data/fishing.yml`） | 百味钓竿 + 5 鱼饵 + 133 掉落 |
| `mob_drops.yml` | `MobDropListener`（EntityDeathEvent） | 106 种食材掉落 |
| `yurenjie/buyunping`、`jurenwan` 等独立脚本 | `SpecialItems`（手写 Java） | 捕云瓶 / 巨人丸 |

> items/foods/machines 引用的 **215 个脚本已全部由 Java 实现**（消耗品 71 + 作物 142 + 钓鱼 + 2 个独立特殊物品）。

## 已知差距（后续可补）

1. **完整实机运行验证**：已确认编译通过、打包完整，全部 16 个 YAML 经解析校验合法。内容已经过**两轮**针对运行期正确性的并行代码审查：第一轮修复了会导致所有机器注册失败的构造期 NPE 等 11 处问题；第二轮（机器/物品行为/加载器数据三路）又修复了 17 处问题（模板槽被背景封死、工作台没电吞输入、作物破坏掉原版物、9 个 mob_drops 因 id_alias 不触发、speed 配置不生效、ARMOR_FORGE 配方丢失等，见 git log）。但**未在真实 MC 服务器中实机加载验证**（本环境无法运行服务端），需在装好前置的服务器实测一次。
2. **作物破坏掉落**：已成熟作物破坏掉落作物/种子（`getDrops` 覆盖为空，且 CropListener 禁用原版掉落，仅由监听器按成熟度掉落，对齐 wt_crop.js）；未成熟破坏不掉落（同原脚本）。
3. **原始内容数据缺口（非本插件引入，安全降级）**：`WT_XIANGYUNCF`（被 items.yml / recipe_machines.yml 引用）、`WT_XUECHENGGQ`（被 mb_machines.yml 引用）两个 id 在全套内容数据中**均无定义**（原 RSC 版同样缺失）。涉及配方在指南中显示为石头（`Read` 警告并回退 STONE），不影响加载。需内容作者补定义或更正 id（不臆测修改）。
4. **button 型物品组**：`groups.yml` 有 38 个 `type: button` 的组，本实现注册为普通子组，点击会打开一个空分页；RSC 中 button 组点击只执行 `actions`（此处全部为 `'none'`）不开分页。差异仅为「多一个空页面」，无功能损坏，可按需对齐。

## 已实现的原脚本框架功能（对照）

- 物品属性：`placeable`、`color`、`id_alias`、`vanilla`、`register.conditions`、`lateInit`、`drop_from/chance/amount`、`anti_wither`、`soulbound`、`radiation`、`piglin_trade_chance`、`energy_capacity`。
- 食物：FoodComponent(`nutrition/saturation/canAlwaysEat`) + `eat_seconds`(反射, Paper 1.21.6+) + onEat 脚本(gz/rou)。
- 机器：电力配方机(`chance/chooseOne/noConsume`)、关联机(**输入/输出均按 `slot:` 绑定**)、模板机(模板门槛)、工作台(点击合成)、多方块机。
- GEO 资源：真实 GEOResource 注册(GEO 采掘机可产出)。
- 脚本行为：215 个脚本全部 Java 实现——消耗品(含 yl/tang/jiu/yan/zhongdu 等药水/空气/冻结/打火石)、作物(142)、钓鱼、生物掉落、捕云瓶、巨人丸。

## 相关链接

- 本地 Slimefun4.1 API 参考：[REF/RykenSlimeCustomizer-1.21.11/REF/Slimefun4.1](../REF/RykenSlimeCustomizer-1.21.11/REF/Slimefun4.1/)
- RSC 29.0 loader/Custom 类（改写时的范式参考）：[REF/RykenSlimeCustomizer-1.21.11/src](../REF/RykenSlimeCustomizer-1.21.11/src/main/java/org/lins/mmmjjkx/rykenslimefuncustomizer/)
- 兼容性背景：[compatibility.md](compatibility.md)
