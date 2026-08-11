# 目录结构与重构说明（2026-08-11）

> 记录仓库从「脚本版资产散落根目录」到「独立插件版为主线 + 脚本版归档」的结构重构。
> 关联 commit 见 `refactor/restructure-legacy` 分支。

## 背景

仓库原本根目录同时堆放「RSC 脚本版资产」（`scripts/`、`info.yml`、13 个内容 yml 等）与「独立插件版」（`plugin/`），主次不清。决策：**仓库以后以独立插件版（`plugin/`）为唯一主线**，脚本版作为可追溯的备用形态归档。

## 重构后目录结构

```
WorldTaste-1.21.11/
├── plugin/                      # 独立插件版（主线）
│   ├── content/                 # 13 个内容 yml（由根目录迁入，git mv 保留历史）
│   ├── src/main/resources/data/ # 脚本参数数据（consumables/crops/fishing）
│   └── build.gradle.kts         # 从 plugin/content 读取内容 yml 打入 jar
├── legacy-rsc/                  # 脚本版归档（自包含、入 git；整目录＝可独立运行的 RSC 附属）
│   ├── scripts/                 # 221 个 JS（git mv）
│   ├── info.yml researches.yml 更新计划.txt 实用工具(可删，不参与加载)/
│   ├── <13 个内容 yml>           # 冻结副本（与 plugin/content 当前一致）
│   └── README.md
├── REF/                         # 第三方只读源码（.gitignore 忽略，不入库；未改动）
├── benchmark/  note/  README.md
```

重构后根目录**不再有**任何 `.yml`、`scripts/`、`更新计划.txt`、`实用工具*/`。

## 变更明细

### 1. 内容 yml 迁入 plugin/content/（主线）
- 13 个内容 yml（`groups/recipe_types/items/foods/machines/recipe_machines/mb_machines/linked_recipe_machines/template_machines/workbenches/mob_drops/geo_resources/menus`）由根目录 `git mv` 至 [../plugin/content/](../plugin/content/)，保留 git 历史。
- [../plugin/build.gradle.kts](../plugin/build.gradle.kts) 的 `processResources` 由 `from(rootProject.projectDir.parentFile)`（仓库根）改为 `from(rootProject.projectDir.resolve("content"))`（plugin/content）。文件名列表与 `into("")` 不变 → 仍打入 jar 根目录，运行期 `plugin.getResource("items.yml")` 行为**逐字不变**。
- 验证：`gradlew clean build` BUILD SUCCESSFUL；jar 含 13 个 content yml（根）+ `data/*.yml` + `plugin.yml`。

### 2. 脚本版归档至 legacy-rsc/
- `scripts/`（221 JS）、`info.yml`、`researches.yml`、`更新计划.txt`、`实用工具(可删，不参与加载)/` 由根目录 `git mv` 至 [../legacy-rsc/](../legacy-rsc/)（保留历史）。
- 13 个内容 yml 的**冻结副本**一并放入 `legacy-rsc/`，使整目录自包含（作为 RSC 附属拷到 `addons/WorldTaste/` 即可运行）。
- 新增 [../legacy-rsc/README.md](../legacy-rsc/README.md) 说明备用形态与加载步骤。

### 3. 不变项
- `plugin/src/**`（Java 源码）、`benchmark/`、`REF/`、`.gitignore`、`plugin/settings.gradle.kts` **均未改动**。
- `.gitignore` 维持 `/REF`（REF 忽略）；`legacy-rsc/` 默认入 git。
- **不升版本号**：`1.8.12-standalone` 不变——纯目录重构，jar 产物与运行行为逐字不变。

## 路径映射表（查阅历史文档时对照）

历史文档（如 [security-audit.md](security-audit.md) 各轮、[release/](release/)）记录的是重构前的位置；按下表映射理解：

| 重构前（根目录） | 重构后 |
|---|---|
| `items.yml` 等 13 个内容 yml | `plugin/content/<name>.yml`（主线）/ `legacy-rsc/<name>.yml`（冻结副本） |
| `scripts/` | `legacy-rsc/scripts/` |
| `info.yml` `researches.yml` | `legacy-rsc/` |
| `更新计划.txt` `实用工具(可删，不参与加载)/` | `legacy-rsc/` |
| `plugin/` `REF/` `benchmark/` `note/` | 位置不变 |

> 本仓库 `note/` 下活跃文档的 markdown 链接已按上表更新；历史日志的链接亦经批量重写指向新位置。
