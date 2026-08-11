# 脚本系统详解

> WorldTaste 的脚本依赖 RSC 的 **GraalVM JS 引擎** 求值。
> 本文所述脚本现归档于 [../legacy-rsc/scripts/](../legacy-rsc/scripts/)（脚本版备用形态）；独立插件版已用 Java 重写其全部行为，见 [standalone-plugin.md](standalone-plugin.md)。
> ⚠️ 仓库内 REF 参考源码为 `29.0-PaperPure`，**已移除该引擎**（见 [compatibility.md](compatibility.md)）。
> 因此本文件描述的是 WorldTaste **目标运行环境（≤28.7-Modified 的 JS-RSC）** 下的脚本行为；
> 其中“JS 全局绑定由 RSC 注入”这一点为依据脚本用法的推断，REF 29.0 源码中已不存在对应注入代码。

## 1. 薄壳 + 公共库模式

绝大多数脚本是高度重复的，已被改写为**薄壳**：读取公共库源码 → `(0, eval)` 执行 → 调用其 `WT_setup*` 函数。

食物脚本薄壳示例（[scripts/1.js](../legacy-rsc/scripts/1.js)）：

```js
var Files  = Java.type('java.nio.file.Files');
var Paths  = Java.type('java.nio.file.Paths');
var rsc    = server.getPluginManager().getPlugin('RykenSlimefunCustomizer').getDataFolder();
var base   = new java.io.File(rsc, 'addons/WorldTaste/scripts');
var code   = new java.lang.String(Files.readAllBytes(Paths.get(base.getPath(), 'lib/wt_food.js')), 'UTF-8');
(0, eval)(code);   // 引入公共库，定义 globalThis.WT_eatConsumable 等

function onUse(event) {
  WT_eatConsumable(event, { food: 1, saturation: 1, exhaustion: 0.1, requireHungry: true });
}
```

- `server`、`Java.type` 等为 **RSC 注入的 JS 全局绑定**（见 §3）。
- `(0, eval)(code)` 在全局作用域执行，使公共库把函数挂到 `globalThis`。

## 2. 公共库 API（scripts/lib/）

### [wt_food.js](../legacy-rsc/scripts/lib/wt_food.js) — 食物进食
- `WT_eatConsumable(event, opts)`：`items.yml` 消耗品（`onUse`）形态。主手消耗 1 个、校验副手不能持粘液物品；按 `opts` 恢复：
  `food`/`saturation`/`exhaustion`（增减）、`saturationSet`/`satRegen`/`unsatRegen`/`starvation`/`maxAir`（直设）、`requireHungry`、`message`、`sound`/`soundName`。
- `WT_eatFood(event, food, sat, exh)`：`foods.yml` 食物（`onEat`）形态，自动进食、不消耗、不校验副手。

### [wt_fishing.js](../legacy-rsc/scripts/lib/wt_fishing.js) — 钓鱼
- `WT_setupFishing(cfg)`：`cfg = { rodId, baits: { 鱼饵id: [{itemId, weight}] } }`。
  在内部把 `onPlayerFish` 绑到 `globalThis`：仅当主手为指定钓竿、副手为已知鱼饵时，取消原掉落、消耗 1 鱼饵、按权重随机产出 1 个物品并拉向玩家、发消息+音效。
- 辅助：`WT_selectRandomDrop`（按 weight 加权随机）、`WT_resolveItemStack`（先查粘液物品，否则原版 Material）、`WT_createDropItemAndEffects`、`WT_sendCatchMessageAndSound`、`WT_decreaseItemInWhichHand`。

### [wt_crop.js](../legacy-rsc/scripts/lib/wt_crop.js) — 作物生长/收获
- 常量 `WT_SMALL_STEPS = [1/10, 1/6, 1/3, 1/2, 2/3, 5/6, 1, 7/6]`（通用小生长阶段）。
- `WT_setupCrop(cfg)`：`cfg = { id, material, maxAge, growMs, stages, spawnTick?, drops?:[{id,chance}], weightedDrops?:[{id,weight}] }`。
  绑定 `tick`（按 `growMs*stages[i]` 推进 Ageable 生长阶段）/ `onPlace`（重置计时）/ `onBreak`（成熟则按规则掉落）。
  每个 `WT_setupCrop` 调用用独立的 `HashMap` 存每方块计时状态（脚本内闭包变量）。
- 辅助：`WT_dropItem`、`WT_selectRandomDrop`。

## 3. JS 全局绑定与事件钩子（依据脚本用法归纳）

脚本中出现的全局符号（来源为 RSC 的脚本引擎注入，**REF 29.0 源码中已无对应实现**）：

| 符号 | 用途（观察） |
|---|---|
| `server` | `server.getPluginManager().getPlugin('RykenSlimefunCustomizer')`，取插件数据目录 |
| `Java.type(FQN)` | 引入 Java 类（`Files`/`Paths`/`Material`/`ItemStack`/`Ageable`/`PotionEffectType` 等） |
| `SlimefunItem` | `SlimefunItem.getByItem(item)` 判断是否粘液物品 |
| `StorageCacheUtils` | `StorageCacheUtils.getSfItem(location)` 取方块上存储的粘液物品 |
| `getSfItemById(id)` | 按 id 取 `SlimefunItem` |
| `getSfItemByItem(item)` | 按物品反查 `SlimefunItem` |

事件钩子函数名（脚本以全局函数形式定义，由 RSC 在对应时机调用）：

| 函数 | 触发 | 用于 |
|---|---|---|
| `onUse(event)` | 玩家右键使用物品 | `items.yml` 消耗品（进食/烟/药等） |
| `onEat(event, player, itemStack)` | 玩家进食 | `foods.yml` 食物 |
| `onPlace(event)` | 放置方块 | 作物种植 |
| `onBreak(event, itemStack, drops)` | 破坏方块 | 作物收获 |
| `tick(info)` | 方块 tick | 作物生长（`info.machine()`/`info.block()`） |
| `onPlayerFish(event)` | 玩家钓鱼事件 | `scriptListener: diaoyu` 注册的全局钓鱼处理 |

> 用法频次（grep 统计）：`WT_setupCrop` 出现 142 次，`onUse` 69 次，`onEat` 8 次，`onPlayerFish`（经 `WT_setupFishing`）3 次。

## 4. 事件监听器（scriptListener）

[info.yml](../legacy-rsc/info.yml) 中 `scriptListener: diaoyu` → RSC 加载 [scripts/diaoyu.js](../legacy-rsc/scripts/diaoyu.js) 并用 ByteBuddy 动态生成 `ScriptableEventListener` 子类注册为 Bukkit 监听器（机制见 [../REF/RykenSlimeCustomizer-1.21.11/note/architecture.md §6](../REF/RykenSlimeCustomizer-1.21.11/note/architecture.md)）。

[scripts/diaoyu.js](../legacy-rsc/scripts/diaoyu.js) 定义了 5 张掉落表（按权重）：

| 鱼饵 id | 主题 | 代表掉落 |
|---|---|---|
| `WT_DANSHUIYUER` | 淡水鱼饵 | 鲑鱼/河豚/鲶鱼/龙虾/鳄鱼/钻石河豚等 |
| `WT_XIANSHUIYUER` | 小型咸水鱼饵 | 鳕鱼/热带鱼/金枪鱼/海星/海胆/鲍鱼等 |
| `WT_XIANSHUIYUER_2` | 大型咸水鱼饵 | 鹦鹉螺/海洋之心/鲨鱼/鲸鱼/帝王蟹等 |
| `WT_SHUIGUOYUER` | 水果鱼饵 | 各色水果鱼/西瓜鲨鱼/海贼王鱼等 |
| `WT_HETUNYUER` | 河豚鱼饵 | 30 种主题河豚（机械/蜜蜂/史莱姆/钻石/金等） |

钓竿 id：`WT_BAIWEIDIAOGAN`（百味钓竿）。掉落综合概率公示见 [items.yml](../plugin/content/items.yml) 中 `WORLDTASTE_GAILV*` 条目。

## 5. 代码生成器（scripts/lib/*.py）

历史上有大量逐字重复的脚本，已用 Python 生成器统一改写为引用公共库（**开发工具，不参与游戏加载**）：

- [gen_refactor.py](../legacy-rsc/scripts/lib/gen_refactor.py)：遍历 `scripts/`，把“结构标准”的食物/作物脚本解析后改写为薄壳。
  - 安全策略：含额外行为（`addPotionEffect`/`setFireTicks`/`damage(`/`spawn` 等列入 `FORBIDDEN_FOOD`）或解析不干净的脚本**保持原样**并报告；已含 lib 引用的跳过；可重复运行。
  - `FOOD_WHITELIST` 强制转写一批已人工确认标准的食物脚本。
- [gen_fishing.py](../legacy-rsc/scripts/lib/gen_fishing.py)：把 [diaoyu.js](../legacy-rsc/scripts/diaoyu.js) 重构为「公共库 + 掉落表数据 + 一行 `WT_setupFishing`」，掉落表与鱼饵映射从原文件自动提取（零数据改动）。

## 6. 独立脚本（不引用公共库，共 29 个）

根目录下以下脚本未使用公共库，含自定义逻辑（多为药水效果/特殊行为）：

```
baoshi.js  bingmianbao.js  fmjpgz.js  fushi.js  jianya.js  jiu.js  jurenwan.js
kangxing.js  maoxian.js  shengcun.js  tang_1.js  tang_4.js ~ tang_8.js
xuejia.js  yan.js  yangqi.js  yangqiguan.js  yl1.js ~ yl7.js
zhongdu.js  zhongdu2.js
```

命名约定（来自 [README.md](../README.md) 与观察）：
- `1.js` ~ `20.js`：仅含数字的脚本，对应恢复的饥饿值/饱和度（已被 `gen_refactor` 转为薄壳）。
- `yan`：香烟；`tang_*`：汤；`yl*`/`yangqi*`：氧气相关；`zhongdu*`：中毒；`jiu`：酒（含随机饥饿+反胃/吸收效果，见 [scripts/jiu.js](../legacy-rsc/scripts/jiu.js)）。
- `gandi/`：无尽贪婪主题；`hetun/`：河豚主题；`yurenjie/`：愚人节主题。
