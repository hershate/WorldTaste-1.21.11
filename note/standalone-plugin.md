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
| `lib/wt_food.js` + 各食物壳（编号 1~20、`tang_*`、`yl_*`、`jiu`、`yan`、`zhongdu*`、`gz*`、`rou`、`baohe`、`huifu`…） | `ConsumableItem`（读 `data/consumables.yml`） | 66 个脚本 |
| `lib/wt_crop.js` + `seed/*`、`gandi/*`、`yurenjie/*` 作物 | `CropBlock`（BlockTicker，读 `data/crops.yml`） | 121 个作物 |
| `diaoyu.js` + `lib/wt_fishing.js` | `FishingListener`（PlayerFishEvent，读 `data/fishing.yml`） | 百味钓竿 + 5 鱼饵 + 133 掉落 |
| `mob_drops.yml` | `MobDropListener`（EntityDeathEvent） | 106 种食材掉落 |

`data/*.yml` 为**手工维护**的行为参数数据（原转换器已弃用）；少数无法对应标准行为的独立脚本回退为普通物品（见下「已知差距」）。

## 已知差距（后续可补）

1. **foods.yml 的自定义 nutrition / onEat（gz/rou）未应用**：食物按其原版材质进食（可食用），但 `nutrition/saturation` 字段与 `gz*/rou` 的额外效果未通过 FoodComponent/事件施加。
2. **workbench（百味万用炉）的“点击合成”简化为自动合成**：原需点击指定槽位触发，现改为有输入即自动合成（linked/template 机器同样简化为通用配方机器）。
3. **geo_resources 未注册为真正的 GEOResource**：GEO 采掘机暂不会产出（物品仍存在）。
4. **作物破坏掉落**：已成熟作物破坏会掉落作物/种子，但未取消原版/粘液方块本身的掉落，可能多掉一份（轻度慷慨）。
5. **少量独立脚本（部分 gandi/yurenjie/单例）未解析**：回退为普通物品，无行为。
6. **运行期未实机验证**：已确认编译通过并打包完整；实际加载/玩法需在装好前置的服务器中验证。

## 相关链接

- 本地 Slimefun4.1 API 参考：[REF/RykenSlimeCustomizer-1.21.11/REF/Slimefun4.1](../REF/RykenSlimeCustomizer-1.21.11/REF/Slimefun4.1/)
- RSC 29.0 loader/Custom 类（改写时的范式参考）：[REF/RykenSlimeCustomizer-1.21.11/src](../REF/RykenSlimeCustomizer-1.21.11/src/main/java/org/lins/mmmjjkx/rykenslimefuncustomizer/)
- 兼容性背景：[compatibility.md](compatibility.md)
