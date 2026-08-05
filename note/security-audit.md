# 稳定性 / 安全性审查日志（独立插件版）

> 目标：在长时间高负载、多用户高频使用下保证稳定与数据安全；**不信任任何用户输入**（破解客户端可发送任意协议包、伪造点击/破坏/放置事件、篡改物品 NBT）。
> 每轮聚焦不同子系统，发现即修复，附 commit 与验证结论。运行期实机验证仍待补（见 [standalone-plugin.md#已知差距](standalone-plugin.md)）。

## 第 1 轮（2026-08-05）：机器与事件处理器的数据操作安全

**范围**：电力配方机 / 多方块机 / 工作台 / 模板机的输入消耗与产出；钓鱼、消耗品进食、生物掉落、作物破坏、方块掉落等玩家可触发路径。重点排查物品**复制 / 丢失 / 幽灵物品 / 竞态**。

### 已修复

| # | 严重度 | 位置 | 缺陷 | 后果 | 修复 / commit |
|---|---|---|---|---|---|
| 1 | 🔴 复制 | `FishingListener.onFish` | 鱼饵 `setAmount(n-1)` 到 0 时副手残留 0 数量幽灵物品，其 Slimefun 绑定不变，仍被 `getByItem` 识别为该鱼饵 | 鱼饵耗尽后可**无消耗无限钓获**（复制） | 到 0 清空副手槽；新增 `Stacks.consumeOneInOffHand` — `7b3f4a6` |
| 2 | 🔴 幽灵/复制 | `ConsumableItem` 主手 | 同源：主手食物到 0 残留幽灵物品 | 0 数量物品被持续识别/显示，可被重复利用 | `Stacks.consumeOneInMainHand` — `52ce414` |
| 3 | 🟠 复制 | `ConsumableItem` 副手打火石 | `yan`（香烟）副手打火石整件消耗（对齐原 `yan.js`），到 0 残留幽灵打火石，仍过 `getType()==FLINT_AND_STEEL` 校验 | **一根打火石无限点烟** | 同上，到 0 清空副手 — `52ce414` |
| 4 | 🟠 资源耗尽 | `WTWorkbench.craft` | `takeCharge` 在匹配前执行，空点击也扣机器能量 | 玩家可反复空点击**刷空共享机器能量**（公共服 grief） | 拆出无消耗 `findMatch`，改为 先匹配→扣能量→消耗→产出 — `a007b7f` |
| 5 | 🟠 逻辑 | `WTRecipeMachine.matchRecipes` | `fitAll`（输出放不下）失败时 `return null`，整体放弃 | 某配方输出放不下会阻塞输出项不同的其它可合成配方，机器空转 | 改为 `continue`；同时拆分 `findMatch`/`consumeMatch` 供工作台复用 — `a007b7f` |

### 验证
- `./gradlew compileJava` 通过（BUILD SUCCESSFUL，仅有既有的 deprecation 提示，非本次引入）。
- 重构为**行为保持**抽取：`matchRecipes = findMatch + consumeMatch`，tick 路径与模板机路径语义不变；仅工作台调用顺序改变（无配方/没电均安全失败，不吞输入）。

### 复查确认（本轮无问题项）
- **多方块机** `WTMultiBlockMachine.onInteract`：消耗走 `ItemUtils.consumeItem` + 输出走 `findOutputInventory`，满则提示并 return（不产出不消耗），与上游 Slimefun `MultiBlockMachine` 同模式；`getContents()` 快照写回为上游既有行为，不擅自改动以免偏移。
- **作物** `CropBlock`：`getDrops()` 返回空 + `CropListener` 禁用原版掉落，仅 `onBreak` 在成熟时按权重/概率掉落一次；非玩家破坏（爆炸/活塞）经 tick 检测到方块被替换后清理 BlockStorage，不刷原版物。
- **生物掉落** `MobDropListener`：`EntityDeathEvent.getDrops().add`，按配置 chance 独立掷一次，无重复。
- **方块掉落** `BlockDrops`：数量区间每次破坏现掷，`clone()` 后投放，无引用泄漏。
- **tick 竞态**：`WTRecipeMachine` 匹配用快照、消耗前对选中槽实时再校验（`stillValid`）；`active` 为 `ConcurrentHashMap`。

### 待办（后续轮次）
- 多方块机 `getContents()` 快照写回的脆弱性（上游共有）—— 评估是否改为显式 `setItem` 提交。
- 破坏机器中途 `onBlockBreak` 丢已消耗输入（上游 AContainer 同行为）—— 评估是否对齐预期。
- 版本号：本轮为关键修复（含复制漏洞），审查周期结束后统一升版并写 release note。
