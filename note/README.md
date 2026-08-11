# 尘世百味 WorldTaste 项目要点

> 本文件夹记录对 **WorldTaste（尘世百味）** 项目的理解要点，供后续开发/维护参考。
> 所有内容基于实际文件阅读整理，关键位置以相对链接给出，便于跳转核对。
> 驱动插件 RSC 的架构详见 [../REF/RykenSlimeCustomizer-1.21.11/note/README.md](../REF/RykenSlimeCustomizer-1.21.11/note/README.md)（只读参考，禁止修改）。

## 一句话简介

WorldTaste 为 Slimefun（粘液科技）添加来自世界各地的美食、作物、钓鱼与屠宰等内容。仓库提供两种等价形态：

- **独立插件版**（[../plugin/](../plugin/)，主线）：单个 Slimefun4.1 附属 jar，由 Java 原生实现，不依赖 RykenSlimefunCustomizer。
- **RSC 脚本版**（[../legacy-rsc/](../legacy-rsc/)，归档备用）：原始形态，纯 YAML 配置 + JavaScript 脚本，作为 RykenSlimeCustomizer 附属加载。

两者内容一致，仅运行方式不同。目录与重构说明见 [repo-structure.md](repo-structure.md)。

- 作者：`haiman233`（见 [../README.md](../README.md)）
- 独立插件版版本：`1.8.12-standalone`（[../plugin/build.gradle.kts](../plugin/build.gradle.kts)、[plugin.yml](../plugin/src/main/resources/plugin.yml)）
- 脚本版版本：`Release-1.8.2`（见 [info.yml](../legacy-rsc/info.yml)）
- 仓库：`haiman233/WorldTaste`

## ⚠️ 关键兼容性提示（针对脚本版，务必先读）

脚本版（[../legacy-rsc/](../legacy-rsc/)）**重度依赖 RSC 的 JavaScript 脚本系统**（`script:` 字段、`scriptListener`、JS 事件钩子）。
而仓库内附带的参考源码 [../REF/RykenSlimeCustomizer-1.21.11/](../REF/RykenSlimeCustomizer-1.21.11/) 实际是 **`29.0-PaperPure`** 版本，该版本已**整体移除 JS 脚本系统**。

**结论：当前 REF 源码无法驱动脚本版**（钓鱼/作物/自定义食物效果/脚本机器全部失效）。
脚本版只能运行在**保留 GraalVM JS 的 RSC（≤ `28.7-Modified` 系列）**之上。
独立插件版不受此影响（已用 Java 重写全部脚本行为）。详见 [compatibility.md](compatibility.md)。

## 前置依赖

独立插件版（见 [plugin.yml](../plugin/src/main/resources/plugin.yml) 与 [../README.md](../README.md)）：

- 硬依赖：`Slimefun`（需适配 Minecraft 1.21.11）
- 硬依赖：`Gastronomicon`（美食家）、`ExoticGarden`（异域花园）
- 软依赖：`Cultivation`（农耕工艺）、`InfinityExpansion`（无尽贪婪）、`LogiTech`

## 顶层结构

| 路径 | 作用 |
|---|---|
| [../plugin/](../plugin/) | **独立插件版（主线）**：Java 源码 + 构建脚本；内容源在 [../plugin/content/](../plugin/content/) |
| [../legacy-rsc/](../legacy-rsc/) | **脚本版归档（备用）**：自包含的 RSC 附属（YAML + JS），见 [../legacy-rsc/README.md](../legacy-rsc/README.md) |
| [../benchmark/](../benchmark/) | 性能微基准（独立插件版） |
| [../REF/](../REF/) | 驱动插件 RSC 与 Slimefun 的**只读参考源码**（禁止修改；被 `.gitignore` 忽略，不入库） |
| [../README.md](../README.md) | 仓库说明（主线为独立插件版） |

## 文档索引

| 文档 | 内容 |
|---|---|
| [repo-structure.md](repo-structure.md) | **目录结构与 2026-08-11 重构说明**（脚本版归档、内容 yml 迁移、路径映射） |
| [architecture.md](architecture.md) | 内容 YAML 结构与条目统计、RSC Reader 映射（脚本版视角）、脚本/公共库整体架构 |
| [scripts.md](scripts.md) | 脚本系统详解：公共库 API、JS 全局绑定、事件钩子、生成器、独立脚本清单 |
| [compatibility.md](compatibility.md) | RSC 版本兼容性：脚本版需 JS-RSC，而 REF 为无脚本的 29.0-PaperPure |
| [standalone-plugin.md](standalone-plugin.md) | **独立 Slimefun4.1 插件改写说明**（plugin/ 工程：架构/构建/脚本行为移植/已知差距） |
| [security-audit.md](security-audit.md) | **稳定性/安全性持续审查日志**（不信任用户输入；57 轮，附修复与 commit） |
| [report/perf/PERF-AUDIT.md](report/perf/PERF-AUDIT.md) | **性能优化持续审查日志**（红线=安全/稳定/兼容；R1–R8，附 benchmark 前后对比） |
| [server-verification-checklist.md](server-verification-checklist.md) | 独立插件版实机验证清单（待真实服务端核验项） |
| [release/](release/) | 各版本基线记录 |
