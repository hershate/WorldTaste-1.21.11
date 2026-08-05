# 尘世百味 WorldTaste 项目要点

> 本文件夹记录对 **WorldTaste（尘世百味）** 项目的理解要点，供后续开发/维护参考。
> 所有内容基于实际文件阅读整理，关键位置以相对链接给出，便于跳转核对。
> 驱动插件 RSC 的架构详见 [../REF/RykenSlimeCustomizer-1.21.11/note/README.md](../REF/RykenSlimeCustomizer-1.21.11/note/README.md)（只读参考，禁止修改）。

## 一句话简介

WorldTaste 是一份 **RykenSlimeCustomizer（RSC）附属配置**：本身不含任何 Java 代码，仅由一组 YAML 配置 + JavaScript 脚本构成，向粘液科技中加入上千种来自世界各地的美食、作物、钓鱼与屠宰系统。

- 作者：`haiman233`（见 [info.yml](../info.yml) `authors`、[README.md](../README.md)）
- 当前版本：`Release-1.8.2`（见 [info.yml](../info.yml) `version`）
- 仓库：`haiman233/WorldTaste`（见 [info.yml](../info.yml) `repo`）
- 安装方式：将整个文件夹放入 `plugins/RykenSlimefunCustomizer/addons/WorldTaste/`（见 [README.md](../README.md)）

## ⚠️ 关键兼容性提示（务必先读）

WorldTaste **重度依赖 RSC 的 JavaScript 脚本系统**（`script:` 字段、`scriptListener`、JS 事件钩子）。
而仓库内附带的参考源码 [../REF/RykenSlimeCustomizer-1.21.11/](../REF/RykenSlimeCustomizer-1.21.11/) 实际是 **`29.0-PaperPure`** 版本，该版本已**整体移除 JS 脚本系统**。

**结论：当前 REF 源码无法驱动 WorldTaste 的脚本部分**（钓鱼/作物/自定义食物效果/脚本机器全部失效）。
WorldTaste 只能运行在**保留 GraalVM JS 的 RSC（≤ `28.7-Modified` 系列）**之上。
详见 [compatibility.md](compatibility.md)。

## 前置依赖

来自 [info.yml](../info.yml) 与 [README.md](../README.md)：

- 硬依赖（RSC 运行所需）：`Slimefun`、`GuizhanLibPlugin`
- 插件硬依赖（`pluginDepends`）：`Gastronomicon`（美食家）、`ExoticGarden`（异域花园，推荐复合花园 fork）
- 软依赖（`softDepends`）：`Cultivation`（农耕工艺）、`InfinityExpansion`（无尽贪婪）

## 顶层结构

| 路径 | 作用 |
|---|---|
| [info.yml](../info.yml) | 附属元信息（id/name/version/scriptListener/pluginDepends…） |
| `*.yml`（根目录） | 各类内容配置，对应 RSC 的 Reader（见 [architecture.md](architecture.md) 内容映射表） |
| [scripts/](../scripts/) | 约 221 个 JS 脚本 + 公共库 + 生成器（见 [scripts.md](scripts.md)） |
| [更新计划.txt](../更新计划.txt) | 作者的后续计划/备忘（非配置，不参与加载） |
| [实用工具(可删，不参与加载)/](../实用工具(可删，不参与加载)/) | 辅助资料（头颅等），不参与加载 |
| [REF/](../REF/) | 驱动插件 RSC 与 Slimefun 的**只读参考源码**（禁止修改） |

## 文档索引

| 文档 | 内容 |
|---|---|
| [architecture.md](architecture.md) | YAML 配置 ↔ RSC Reader 映射表（含条目数）、脚本/公共库整体架构 |
| [scripts.md](scripts.md) | 脚本系统详解：公共库 API、JS 全局绑定、事件钩子、生成器、独立脚本清单 |
| [compatibility.md](compatibility.md) | RSC 版本兼容性：WorldTaste 需 JS-RSC，而 REF 为无脚本的 29.0-PaperPure |
| [standalone-plugin.md](standalone-plugin.md) | **独立 Slimefun4.1 插件改写说明**（plugin/ 工程：架构/构建/脚本行为移植/已知差距） |
| [security-audit.md](security-audit.md) | **稳定性/安全性持续审查日志**（不信任用户输入；每轮覆盖不同子系统，附修复与 commit） |
| [report/perf/PERF-AUDIT.md](report/perf/PERF-AUDIT.md) | **性能优化持续审查日志**（红线=安全/稳定/兼容；每轮一优化点，附 benchmark 与前后对比） |
| [release/](release/) | 各版本基线记录 |
