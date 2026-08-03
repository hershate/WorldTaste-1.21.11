# 架构与内容映射

## 1. 项目性质

WorldTaste **不是 Java 项目**，而是一份 RSC 附属的**纯配置 + 脚本**资产：

- **YAML 配置**：定义物品、机器、配方、物品组、菜单等，由 RSC 的各类 `Reader` 加载（Reader 映射详见 [../REF/RykenSlimeCustomizer-1.21.11/note/content-types.md](../REF/RykenSlimeCustomizer-1.21.11/note/content-types.md)，只读参考）。
- **JavaScript 脚本**：实现需要逻辑的行为（食物效果、作物生长、钓鱼掉落等），由 RSC 的 GraalVM JS 引擎求值（见 [scripts.md](scripts.md)）。

> 注意：RSC 各 Reader 的加载顺序固定（groups → recipe_types → … → researches），
> 详见 [../REF/RykenSlimeCustomizer-1.21.11/note/architecture.md §3](../REF/RykenSlimeCustomizer-1.21.11/note/architecture.md)。

## 2. YAML 配置文件 ↔ 内容（含条目数统计）

下表“条目数”为各 yml 顶层 key 的粗略计数（`grep -cE "^[A-Za-z_][A-Za-z0-9_]*:"`），仅作规模参考；

| 配置文件 | RSC Reader（参考） | 产出类型 | 条目数 | 说明 |
|---|---|---|---|---|
| [info.yml](../info.yml) | Loader 直读 | 元信息 | — | id/name/version/`scriptListener: diaoyu`/pluginDepends/loadStartTexts |
| [groups.yml](../groups.yml) | `ItemGroupReader` | `RSCItemGroup` | 74 | 物品组/分类（`worldtaste` 根 + `ws_*` 子组/按钮/嵌套） |
| [recipe_types.yml](../recipe_types.yml) | `RecipeTypesReader` | `CustomRecipeType` | 40 | 自定义配方类型（五味厨房/发酵箱/烘焙炉/瓦锅/炸锅/饮料机/卷烟台/屠宰/钓鱼…） |
| [mob_drops.yml](../mob_drops.yml) | `MobDropsReader` | `CustomMobDrop` | 106 | 生物掉落（屠宰系统产出的肉等） |
| [geo_resources.yml](../geo_resources.yml) | `GeoResourceReader` | `CustomGeoResource` | 1 | GEO 资源 |
| [items.yml](../items.yml) | `ItemReader` | `CustomItem` 各子类 | ~3094 | **主体**（2.5MB）：种子/食材/鱼类/工具/消耗品/装饰等，含 `script:` 钩子 |
| [foods.yml](../foods.yml) | `FoodReader` | `CustomFood` | 189 | 原版式食物（`onEat` 自动进食脚本） |
| [menus.yml](../menus.yml) | `MenuReader` | `CustomMenu` | 24 | 自定义 GUI 菜单 |
| [machines.yml](../machines.yml) | `MachineReader` | `CustomMachine`/`CustomNoEnergyMachine` | 142 | 通用机器（部分为脚本驱动） |
| [recipe_machines.yml](../recipe_machines.yml) | `RecipeMachineReader` | `CustomRecipeMachine` | 21 | 配方驱动机器 |
| [mb_machines.yml](../mb_machines.yml) | `MultiBlockMachineReader` | `CustomMultiBlockMachine` | 15 | 多方块机器（文件 75k 行：配方内联在 `recipes` 段） |
| [template_machines.yml](../template_machines.yml) | `TemplateMachineReader` | `CustomTemplateMachine` | 2 | 模板机器 |
| [linked_recipe_machines.yml](../linked_recipe_machines.yml) | `LinkedRecipeMachineReader` | `CustomLinkedRecipeMachine` | 4 | 关联输出配方机器 |
| [workbenches.yml](../workbenches.yml) | `WorkbenchReader` | `CustomWorkbench` | 2 | 工作台（百味万用炉等） |
| [researches.yml](../researches.yml) | `ResearchReader` | `Research` | 0 | **空**（本附属未使用科技解锁） |

> 说明：本仓库不含 `armors.yml`/`capacitors.yml`/`generators.yml`/`solar_generators.yml`/`mat_generators.yml`/`simple_machines.yml`/`supers.yml`/`super_multi_block_machines.yml`/`generations.yml`，对应能力未使用。

### 物品 ID 命名约定（观察）

- `WT_` 前缀：WorldTaste 自有物品（如 `WT_BAIWEIDIAOGAN` 百味钓竿、`WT_SEED_AICAO` 艾草种子）。
- `GN_` 前缀：引用 Gastronomicon（美食家）物品（如 `GN_RAW_TUNA`）。
- 原版 Material：直接用枚举名（如 `COD`、`SALMON`、`NAUTILUS_SHELL`）。
- 配方类型 `WUWEI_*`：五味系列自定义机器（见 [recipe_types.yml](../recipe_types.yml)）。

### `items.yml` 物品结构示例

种子条目典型结构（见 [items.yml](../items.yml) 顶部）：

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

[README.md](../README.md)「致开发者」明确：若 `items.yml` 里使用了自定义多方块机器，需在 [mb_machines.yml](../mb_machines.yml) 找到对应多方块，**把配方额外再列一份**到该多方块的 `recipes` 段。这是 mb_machines.yml 体积巨大（75k 行）的主要原因。

## 4. 脚本 / 公共库架构（概览）

脚本采用**薄壳 + 公共库**模式：每个脚本只 `(0, eval)` 引入一个公共库，再用一行配置/数据调用其 `WT_setup*` 函数。详见 [scripts.md](scripts.md)。

```
scripts/
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
