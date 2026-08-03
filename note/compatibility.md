# ⚠️ RSC 版本兼容性（关键）

> 本文件记录一个**经源码核实的硬性不兼容**，会直接影响所有后续操作，务必先读。

## 结论

**WorldTaste 需要“带 JavaScript 脚本系统”的 RSC 才能正常工作；而仓库附带的 REF 参考源码是“已移除脚本系统”的 `29.0-PaperPure` 版本，二者不兼容。**

若用 REF 的 29.0-PaperPure 运行 WorldTaste，以下功能将**全部失效**（且很可能静默失效）：

- 钓鱼系统（`scriptListener: diaoyu`）
- 全部作物种植/生长/收获（`scripts/seed/*` 等共 142 处 `WT_setupCrop`）
- 自定义食物/消耗品效果（`onUse`/`onEat`，约 77 处）
- 任何 `machines.yml` 中由脚本驱动的机器（不再自动合成）
- 所有“独立逻辑”脚本（酒/烟/汤/氧气/中毒等 29 个）

## 证据

### 1. WorldTaste 依赖脚本系统

- [info.yml](../info.yml) 第 11 行：`scriptListener: diaoyu`（注册全局事件监听器）。
- [items.yml](../items.yml) 大量条目带 `script:` 字段（如 `WT_SEED_XHLB` → `script: seed/xhlb`）。
- `scripts/` 约 221 个 JS 文件，依赖 RSC 注入的 `server`/`getSfItemById`/`getSfItemByItem`/`SlimefunItem`/`StorageCacheUtils` 等全局绑定（见 [scripts.md §3](scripts.md)）。
- 这些能力属于 RSC 的 **GraalVM JS 脚本系统**（`JavaScriptEval`/`ScriptEval`/`ScriptableEventListener`/ByteBuddy 动态监听器）。

### 2. REF 源码实际版本 = 29.0-PaperPure（已移除 JS）

- [../REF/RykenSlimeCustomizer-1.21.11/build.gradle.kts](../REF/RykenSlimeCustomizer-1.21.11/build.gradle.kts)：`version = "29.0-PaperPure"`。
- 其版本记录 [../REF/RykenSlimeCustomizer-1.21.11/note/release/29.0-PaperPure.md](../REF/RykenSlimeCustomizer-1.21.11/note/release/29.0-PaperPure.md) 明确：
  > “脚本系统（GraalVM JS）整体移除……连锁移除：`JavaScriptEval`、`ScriptEval`、`ScriptableEventListener`、`ScriptedEvalBreakHandler`、所有机器/物品/菜单/按钮的 JS 钩子、`scriptListener`/`configHandler`、超大多方块脚本部件……`machines.yml` 的脚本驱动机器不再自动合成。”
- 源码核实：REF 的 `src/` 中**无 `bulit_in` 包、无 `objects/script` 包、无任何 GraalVM/polyglot/ScriptEngine 引用**；`ItemReader.java` 不含 `script` 字段处理。`grep "script"` 命中的 19 个文件均为 `description` 等无关子串。

### 3. REF/note 自身存在过时描述（仅提示，不改 REF）

- [../REF/RykenSlimeCustomizer-1.21.11/note/README.md](../REF/RykenSlimeCustomizer-1.21.11/note/README.md) 仍写“当前版本：`28.7-Modified`”，与实际源码（29.0）不符。
- [../REF/RykenSlimeCustomizer-1.21.11/note/architecture.md](../REF/RykenSlimeCustomizer-1.21.11/note/architecture.md) 详述了 GraalVM JS 脚本系统，但这部分描述的是 **28.7 时代**的架构，在 29.0 源码中已不存在。
- （REF 为只读参考，按约定不修改；仅在此记录该不一致，避免后续被误导。）

## 适配目标（待用户确认）

后续所有针对 WorldTaste 的开发/调试，需先明确“以哪个 RSC 为准”：

- **方案 A（推荐，若目标是让 WorldTaste 正常运行）**：以**保留 GraalVM JS 的 RSC（≤ `28.7-Modified`）** 为运行/参考环境。此时 REF 内的 29.0 源码**不能**作为脚本行为的依据，应改用 28.7-Modified 系列源码或其发布 jar。
- **方案 B（若目标是把 WorldTaste 迁移到 29.0-PaperPure）**：需把全部脚本逻辑改写为**非脚本**实现——例如把作物/钓鱼/食物效果迁移到 `recipe_machines`/`foods` 等纯配置能力，或用 `supers.yml` 反射继承等 29.0 仍保留的机制。工作量很大且部分行为（如按权重随机钓鱼掉落）难以纯配置等价表达。

> 该选择直接影响后续所有操作的方向，**请用户明确指定目标 RSC 版本**后再继续。

## 相关链接

- RSC 脚本系统机制（28.7 时代描述）：[../REF/RykenSlimeCustomizer-1.21.11/note/architecture.md §6](../REF/RykenSlimeCustomizer-1.21.11/note/architecture.md)
- 29.0 移除清单：[../REF/RykenSlimeCustomizer-1.21.11/note/release/29.0-PaperPure.md](../REF/RykenSlimeCustomizer-1.21.11/note/release/29.0-PaperPure.md)
- WorldTaste 脚本清单：[scripts.md](scripts.md)
