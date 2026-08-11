# 架构与内容映射

## 1. 项目性质

本文描述 WorldTaste **内容 YAML** 的结构与条目统计。这套内容 yml 脚本版与独立插件版**共用同一份**（现位于 [../plugin/content/](../plugin/content/)；脚本版归档另有一份冻结副本在 [../legacy-rsc/](../legacy-rsc/)）：

- **YAML 配置**：定义物品、机器、配方、物品组、菜单等。脚本版由 RSC 各 `Reader` 加载（Reader 映射详见 [../REF/RykenSlimeCustomizer-1.21.11/note/content-types.md](../REF/RykenSlimeCustomizer-1.21.11/note/content-types.md)，只读参考）；独立插件版由自带 Loader 加载（见 [standalone-plugin.md](standalone-plugin.md)）。
- **JavaScript 脚本**（仅脚本版）：实现需要逻辑的行为（食物效果、作物生长、钓鱼掉落等），由 RSC 的 GraalVM JS 引擎求值（见 [scripts.md](scripts.md)）；独立插件版已用 Java 原生重写全部脚本行为，不再依赖 JS。

> 注意：RSC 各 Reader 的加载顺序固定（groups → recipe_types → … → researches），
> 详见 [../REF/RykenSlimeCustomizer-1.21.11/note/architecture.md §3](../REF/RykenSlimeCustomizer-1.21.11/note/architecture.md)。

## 2. YAML 配置文件 ↔ 内容（含条目数统计）

下表“条目数”为各 yml 顶层 key 的粗略计数（`grep -cE "^[A-Za-z_][A-Za-z0-9_]*:"`），仅作规模参考；

| 配置文件 | RSC Reader（参考） | 产出类型 | 条目数 | 说明 |
|---|---|---|---|---|
| [info.yml](../legacy-rsc/info.yml) | Loader 直读 | 元信息 | — | id/name/version/`scriptListener: diaoyu`/pluginDepends/loadStartTexts |
| [groups.yml](../plugin/content/groups.yml) | `ItemGroupReader` | `RSCItemGroup` | 74 | 物品组/分类（`worldtaste` 根 + `ws_*` 子组/按钮/嵌套） |
| [recipe_types.yml](../plugin/content/recipe_types.yml) | `RecipeTypesReader` | `CustomRecipeType` | 40 | 自定义配方类型（五味厨房/发酵箱/烘焙炉/瓦锅/炸锅/饮料机/卷烟台/屠宰/钓鱼…） |
| [mob_drops.yml](../plugin/content/mob_drops.yml) | `MobDropsReader` | `CustomMobDrop` | 106 | 生物掉落（屠宰系统产出的肉等） |
| [geo_resources.yml](../plugin/content/geo_resources.yml) | `GeoResourceReader` | `CustomGeoResource` | 1 | GEO 资源 |
| [items.yml](../plugin/content/items.yml) | `ItemReader` | `CustomItem` 各子类 | ~3094 | **主体**（2.5MB）：种子/食材/鱼类/工具/消耗品/装饰等，含 `script:` 钩子 |
| [foods.yml](../plugin/content/foods.yml) | `FoodReader` | `CustomFood` | 189 | 原版式食物（`onEat` 自动进食脚本） |
| [menus.yml](../plugin/content/menus.yml) | `MenuReader` | `CustomMenu` | 24 | 自定义 GUI 菜单 |
| [machines.yml](../plugin/content/machines.yml) | `MachineReader` | `CustomMachine`/`CustomNoEnergyMachine` | 142 | 通用机器（部分为脚本驱动） |
| [recipe_machines.yml](../plugin/content/recipe_machines.yml) | `RecipeMachineReader` | `CustomRecipeMachine` | 21 | 配方驱动机器 |
| [mb_machines.yml](../plugin/content/mb_machines.yml) | `MultiBlockMachineReader` | `CustomMultiBlockMachine` | 15 | 多方块机器（文件 75k 行：配方内联在 `recipes` 段） |
| [template_machines.yml](../plugin/content/template_machines.yml) | `TemplateMachineReader` | `CustomTemplateMachine` | 2 | 模板机器 |
| [linked_recipe_machines.yml](../plugin/content/linked_recipe_machines.yml) | `LinkedRecipeMachineReader` | `CustomLinkedRecipeMachine` | 4 | 关联输出配方机器 |
| [workbenches.yml](../plugin/content/workbenches.yml) | `WorkbenchReader` | `CustomWorkbench` | 2 | 工作台（百味万用炉等） |
| [researches.yml](../legacy-rsc/researches.yml) | `ResearchReader` | `Research` | 0 | **空**（本附属未使用科技解锁） |

> 说明：本仓库不含 `armors.yml`/`capacitors.yml`/`generators.yml`/`solar_generators.yml`/`mat_generators.yml`/`simple_machines.yml`/`supers.yml`/`super_multi_block_machines.yml`/`generations.yml`，对应能力未使用。

### 物品 ID 命名约定（观察）

- `WT_` 前缀：WorldTaste 自有物品（如 `WT_BAIWEIDIAOGAN` 百味钓竿、`WT_SEED_AICAO` 艾草种子）。
- `GN_` 前缀：引用 Gastronomicon（美食家）物品（如 `GN_RAW_TUNA`）。
- 原版 Material：直接用枚举名（如 `COD`、`SALMON`、`NAUTILUS_SHELL`）。
- 配方类型 `WUWEI_*`：五味系列自定义机器（见 [recipe_types.yml](../plugin/content/recipe_types.yml)）。

### `items.yml` 物品结构示例

种子条目典型结构（见 [items.yml](../plugin/content/items.yml) 顶部）：

```yaml
WT_SEED_XHLB:
  item_group: ws_zhongzi
  item:
    name: "&f&l小红萝卜种子"
    material_type: skull_hash        # 头颅材质（hash）或 mc（原版）/ slimefun
    material: <sha256 hash>
    lore: [...]
  recipe: { 5: { material_type: slimefun, material: WT_SCZZ, amount: 1 } }
  recipe_type: WUWEI_ZZJDY           # 种子鉴定仪
  script: seed/xhlb                  # ← 指向 scripts/seed/xhlb.js（作物脚本）
```

> `material_type` 取值：`skull_hash`（玩家头颅材质哈希）、`mc`（原版 Material）、`slimefun`（粘液物品 id）。

## 3. 多方块机器配方“双份”约定

[README.md](../README.md)「致开发者」明确：若 `items.yml` 里使用了自定义多方块机器，需在 [mb_machines.yml](../plugin/content/mb_machines.yml) 找到对应多方块，**把配方额外再列一份**到该多方块的 `recipes` 段。这是 mb_machines.yml 体积巨大（75k 行）的主要原因。

## 4. 脚本 / 公共库架构（概览）

脚本采用**薄壳 + 公共库**模式：每个脚本只 `(0, eval)` 引入一个公共库，再用一行配置/数据调用其 `WT_setup*` 函数。详见 [scripts.md](scripts.md)。

```
legacy-rsc/scripts/
├── lib/                 # 公共库（被 eval 引入，不直接作 script 字段）
│   ├── wt_food.js       # 食物进食逻辑（WT_eatConsumable / WT_eatFood）
│   ├── wt_fishing.js    # 钓鱼逻辑（WT_setupFishing）
│   ├── wt_crop.js       # 作物生长/收获（WT_setupCrop）
│   ├── gen_refactor.py  # 生成器：把重复的食物/作物脚本改写为引用公共库
│   └── gen_fishing.py   # 生成器：把 diaoyu.js 改写为薄壳
├── seed/   (119)        # 各作物脚本（调 WT_setupCrop）
├── gandi/  (5)          # 「贪婪/无尽」主题相关
├── hetun/  (1)          # 河豚主题
├── yurenjie/(13)        # 愚人节主题
└── *.js    (58)         # 根目录：食物消耗(1~20.js 等)、diaoyu.js(监听器)、独立逻辑脚本
```

统计（实测）：约 221 个文件；其中 **172 个**通过 `eval` 引用 `lib/wt_*.js`；**29 个**根目录脚本为独立逻辑（酒/烟/汤/氧气/中毒等，见 [scripts.md](scripts.md) §独立脚本）。

## 5. 性能优化（独立插件版，持续进行）

独立插件的性能优化逐轮记录于 [report/perf/PERF-AUDIT.md](report/perf/PERF-AUDIT.md)，配套微基准在 [benchmark/](../benchmark/)。
覆盖两条维度：
- **运行期热路径（R1–R5，已闭合）**：per-tick `WTRecipeMachine.findMatch`（SF-id 预筛 + 机器级闸门、posBySlot 不变量）、
  `CropBlock.tick`（growMsSteps 不变量；Location 分配消除方案经实测劣化已拒）、事件驱动（Fishing total 预算）、低频（getDisplayRecipes 缓存）。
- **加载期（R6–R7，趋收敛）**：`Yaml.loadResource` 文件名缓存——`preloadDisplays` 与各 Loader 共 10 个内容文件由「解析两次」→「解析一次」，
  `Setup.loadAll` 末尾 `clearCache` 释放解析树（长稳，R6）；`Read.resolve` 对头颅贴图(PlayerSkin)按 (类型,材质) 去重缓存（实测重复率 ~6%，
  dough 无内部缓存，R7），加载后 `clearSkinCache` 释放。
所有优化均行为保持（对齐 RSC 保真度）、零回归，逐轮附基准前后对比。
- **R8 全维度最终扫描**：复核 MobDrop（已最优）/pushOutputs（R5 判断正确）/CropBlock.onBreak（修复 R4 遗漏：收获加权选择 total 改用 load 期预算）；
  **静态性能优化全维度收敛**（per-tick/事件/低频/加载期均已覆盖，剩余为不可约项或需实机 profile）。
