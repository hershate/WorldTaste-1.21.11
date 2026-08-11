# legacy-rsc（尘世百味 RSC 脚本版备用归档）

本目录是 WorldTaste 的**原始 RSC 附属形态**（纯 YAML 配置 + GraalVM JavaScript 脚本），作为**冻结的备用归档**保留。

仓库主线已切换为**独立 Slimefun4.1 插件**（[../plugin/](../plugin/)），由 Java 原生实现了本目录中全部脚本的行为。两种形态内容等价，仅运行方式不同（见 [../note/compatibility.md](../note/compatibility.md)）。

## 自包含说明

本目录等同于一个完整的 RykenSlimeCustomizer 附属：将其整体作为 `addons/WorldTaste/` 即可按脚本版方式加载。

## 作为 RSC 附属加载

1. 安装**保留 GraalVM JS 脚本系统**的 RykenSlimeCustomizer（≤ `28.7-Modified` 系列）。
2. 将本目录整个文件夹放入 `plugins/RykenSlimefunCustomizer/addons/WorldTaste/`。
3. 装齐前置：Slimefun、Gastronomicon（美食家）、ExoticGarden（异域花园）。
4. 重启服务器（不建议热重载）。

> ⚠️ **不兼容警告**：仓库 [../REF/](../REF/) 内附带的 RSC 参考源码为 `29.0-PaperPure`，**已整体移除 JS 脚本系统**，无法驱动本目录的任何脚本（钓鱼/作物/食物效果/脚本机器将全部静默失效）。务必使用带 JS 引擎的 RSC。详见 [../note/compatibility.md](../note/compatibility.md)。

## 目录内容

| 内容 | 说明 |
|---|---|
| `info.yml` | RSC 附属元信息（id / `scriptListener: diaoyu` / 版本 `Release-1.8.2`） |
| `scripts/` | 约 221 个 JS 脚本 + 公共库（`lib/`）+ 代码生成器，详见 [../note/scripts.md](../note/scripts.md) |
| 13 个内容 `*.yml` | items/foods/machines/mob_drops/groups/recipe_types/menus/… 与独立插件版当前一致 |
| `researches.yml` | 空（本附属未使用科技解锁） |
| `更新计划.txt` | 作者的历史备忘（非配置，不参与加载） |
| `实用工具(可删，不参与加载)/` | 辅助资料（头颅、参考脚本等），不参与加载 |

## 与独立插件版的关系

- 本目录的 13 个内容 yml 与 [../plugin/content/](../plugin/content/) **当前内容一致**，但本目录为冻结快照、不再演进；后续内容修订只在 `plugin/content/` 进行。
- 独立插件版的脚本参数数据（消耗品/作物/钓鱼）见 [../plugin/src/main/resources/data/](../plugin/src/main/resources/data/)，已由 Java 实现，不再依赖本目录的 JS。
