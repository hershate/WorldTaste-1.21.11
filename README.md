# 尘世百味 WorldTaste

<img width="220" height="220" alt="worldtaste" src="https://github.com/user-attachments/assets/89593566-830a-466a-b8f2-6cd2b2459d0b" />

尘世百味为 Slimefun（粘液科技）添加来自世界各地的美食、作物、钓鱼与屠宰等内容。

> 本仓库提供两种形态，内容一致，仅运行方式不同：
> - **独立插件版**（[plugin/](plugin/)，主线）：单个 jar，不依赖 RykenSlimefunCustomizer，放入 `plugins/` 即可。
> - **RSC 脚本版**（[legacy-rsc/](legacy-rsc/)，历史形态）：纯 YAML 配置 + JavaScript 脚本，作为 RykenSlimefunCustomizer 附属加载（自包含，详见 [legacy-rsc/README.md](legacy-rsc/README.md)）。

## 当前状态

- 独立插件版：`1.8.12-standalone`（见 [plugin/build.gradle.kts](plugin/build.gradle.kts)、[plugin.yml](plugin/src/main/resources/plugin.yml)）。
- 已完成多轮静态审查，覆盖安全性、性能与对原脚本的行为保真度，记录在 [note/](note/)。
- ⚠️ 诚实声明：**尚未在真实服务端实机验证**。编译与打包通过、内容 YAML 经解析校验合法，但建议在投入使用前先在测试服完整跑一次（参考 [note/server-verification-checklist.md](note/server-verification-checklist.md)）。

## 前置依赖

| 类型 | 插件 |
|---|---|
| 必须 | Slimefun（需适配 Minecraft 1.21.11 的版本） |
| 必须 | [Gastronomicon](https://builds.guizhanss.com/SlimefunGuguProject/Gastronomicon/master)（美食家）、[ExoticGarden](https://builds.guizhanss.com/balugaq/ExoticGardenComplex/master)（异域花园·复合花园 fork） |
| 可选 | [Cultivation](https://builds.guizhanss.com/SlimefunGuguProject/Cultivation/main)（农耕工艺）、[InfinityExpansion](https://builds.guizhanss.com/SlimefunReloadingProject/InfinityExpansion/master)（无尽贪婪）、LogiTech |

> 提示：若 Gastronomicon 的捕鱼网拉低 TPS，可在其配置中禁用捕鱼网（粘液 ID `GN_FISHING_NET_I/II/III`），或改用本附属性能更优的捕鱼器。

## 构建与安装（独立插件版）

```bash
cd plugin
./gradlew build
# 产物：plugin/build/libs/WorldTaste-1.8.12-standalone.jar
```

1. 将构建出的 jar 放入服务器的 `plugins/` 目录。
2. 装齐上表中的前置插件。
3. 重启服务器（不建议热重载）。

构建说明：

- 需要 **JDK 21**（Gradle `toolchain` 已固定；用仓库自带的 `./gradlew` 即可，无需单独安装 Gradle）。
- 编译期依赖 Paper 1.21.11 API（自动拉取）与 `REF/` 内的适配版 Slimefun4.1（`compileOnly`，不打包进 jar）。⚠️ `REF/` 未纳入 git（见 `.gitignore`），贡献者需自行准备该 Slimefun4.1 jar。
- 运行期内容 YAML（来自 [`plugin/content/`](plugin/content/)）与脚本参数数据（`data/*.yml`）已一并打入 jar，产物自包含，放入 `plugins/` 即可运行。

## 功能概览

- **食物**：烘焙、肉食、中餐、汤与炖菜、饮品（酿酒/果汁）、甜品、零食、发酵食品、功能丸子等十余个分类。
- **作物**：多种作物及其变种，带生长与收获机制。
- **钓鱼**：百味钓竿搭配 5 种鱼饵，按权重掉落各类鱼产。
- **屠宰**：为各类生物添加对应的肉与食材掉落。
- **其他**：厨房装饰，以及愚人节 / 无尽贪婪主题餐饮。

## RSC 脚本版（备选）

若希望以 RykenSlimefunCustomizer 附属方式使用：

1. 安装保留 GraalVM JS 脚本系统的 RSC（≤ `28.7-Modified` 系列）。
2. 将 [legacy-rsc/](legacy-rsc/) 整个文件夹放入 `plugins/RykenSlimefunCustomizer/addons/WorldTaste/`（它自包含全部脚本与配置）。
3. 重启服务器。

> ⚠️ 注意：本仓库 `REF/` 内附带的 RSC 参考源码为 `29.0-PaperPure`，**已移除 JS 脚本系统**，无法驱动脚本版。详见 [note/compatibility.md](note/compatibility.md)。

## 致谢

感谢 [balugaq](https://github.com/balugaq) 编写的 [rsc-editor](https://github.com/balugaq/RSCEditor)，以及 balugaq、Eventually、南柯梦在脚本编写上给予的帮助。

## 文档

项目设计与审查要点见 [note/](note/)，索引见 [note/README.md](note/README.md)。各版本基线记录见 [note/release/](note/release/)。
