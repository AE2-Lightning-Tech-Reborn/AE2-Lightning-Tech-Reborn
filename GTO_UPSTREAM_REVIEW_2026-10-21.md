# 上游新增提交核查（2026-10-21）

已执行 `git fetch upstream --prune`，并通过 `git ls-remote` 核对远端分支。对照对象是当前包含未提交移植和 NeoECO 清除的 LT `2.1.2-gto.5` 工作树，而非仅比较本地 HEAD。此次仅更新远端引用、检查代码并保存本报告，没有合并、移植、构建、提交或推送。

## 当前上游

| 仓库 / 分支 | 最新提交 | 相对上次已获取引用 |
| --- | --- | --- |
| LT `gto` | `cb80004f75af0fdde12e974312d5f0a3667f05fe` | 没有更新 |
| LT `1.20.1` | `c568e6751b7e78bb556ba2a90dc7b3a736196f1d` | 从 `2228757b` 新增 7 次提交 |
| LT `alpha` | `e337a8306067e95faae373fe903c6e49e2c41205` | 从 `db4a8587` 新增 3 次提交 |
| LT `main` | `3dee4d98c716273179a6d86b9df3d2c0d7347155` | 从 `aaa9642d` 新增 2 次提交 |
| Core `gto` | `7c7a066af9d404010fe0b9472fca687879b36281` | 远端仍与此前记录一致 |
| Core `1.20.1` | `cea4cc29b97e308987ad4a06a2c61f7fb00f0527` | 远端仍与此前记录一致 |
| Core `alpha` | `28f5f8f3fcc33f384ce4383e5a2af8f4b00e05a0` | 远端仍与此前记录一致 |
| Core `main` | `bb2f2ab0ad48ef3b53087327fe833d4e1743eb98` | 远端仍与此前记录一致 |

分支上的相同修复可能具有不同提交哈希，不能按分支提交数量直接合计独立改进数。

## 优先可移植内容

### 1. 终端客户端广播保护：当前缺失

[alpha `e337a830`](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/commit/e337a8306067e95faae373fe903c6e49e2c41205)，对应 main `3dee4d98`。

`BigTerminalMenuMixin.ae2lt$syncBigStock` 在读取 AE2 服务端专用的 `getPreviousAvailableStacks()` **之前**检查 `menu.isClientSide()`。部分附属终端（上游用 WCWT 清空按钮复现）会在客户端调用 `broadcastChanges`，原实现可能在读取库存时异常。

本地仍直接求值 `getPreviousAvailableStacks()` 再传给同步器，缺少这道提前返回。适合优先移植，保留本地 Forge/GTO `broadcastChanges()V` 的 remap 设置。上游附带可执行注入回归和 WCWT 客户端探针；探针的 NeoForge 事件、构建脚本及所需 WCWT 版本需另行适配。

### 2. 无线供应器补料周期公平性：当前缺失

[alpha `c088c5df`](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/commit/c088c5df9177017e9102e42ffb47024cf55c3f6d)。

正常等待补料的目标继续参与 100 tick 派发统计，避免被当作不可用目标反复补发调度信用。批量增长只能在均衡窗口突破常规额度；单份派发改用每个配方独立的公平统计，避免交错配方长期固定投给某台机器。

本地 `DispatchFairnessScheduler.java` 与该提交的父版本逐字一致，缺少 `refillCoverage`、窗口参与统计和 `raiseRampAllowance`；`ProviderWirelessDispatch` 也缺少新增的 `dispatchFairSingleCopy` 路径。应配套移植两个生产类和相关回归，保留本地其他提供器适配。

此修复针对无线提供器目标分配；不能认定它解决当前已知的时轮 CPU 替代材料唤醒失败。

### 3. 设备权限、领地保护和频率限制：当前缺失

[main `3c7df23f`](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/commit/3c7df23fb37cd865cd321e4800bfb3b848188fe7)。

将频率卡、连接器、记忆卡导入和延迟放置等操作交给玩家交互/放置保护事件，检查操作玩家与目标。新增每玩家 OWNER 频率上限（默认 16，降低配置时保留已有成员关系），并对密码尝试实施每玩家跨频率的一秒五次滚动限流。

本地没有 `PlayerWorldActions`、`PasswordAttemptWindow`、`FrequencyPasswordAttempts`；频率创建没有 `canOwnAnotherFrequency` 检查，记忆卡导入仍使用不带玩家/目标的旧入口。适合独立移植，但涉及 37 个文件，应将 NeoForge 事件、快照回滚和数据组件转换为 Forge 1.20.1 的事件、NBT 与现有网络接口。需要完整保留并验证放置失败回滚、频率切换/解绑、权限与领地保护回归。

## Forge 分支增量：避免重复移植

`1.20.1` 从上次引用新增：

| 提交 | 内容 | 当前 GTO 判断 |
| --- | --- | --- |
| `02b538d6` | IO 重读空槽清理、自由视角身份隔离 | 已有此前等效移植 |
| `9ef4b5e5` | 无限元件摘要避免复制 | 已有此前等效移植 |
| `1441ef02` | 最终合成派发与精确回料 | 主要内容已移植；NeoECO 专用路径按要求已清除 |
| `948676aa` | Mekanism / 工业先锋回料适配 | 已在 gto.4 移植，gto.5 保留 |
| `83f5d577` | 按 JEI 版本选择旧/新转移入口 | 本地没有这套版本分流；需按 GTO 实际 JEI/EMI API 审核，不能直接替换插件 |
| `7ba14fd2` | 精确派发切片受物理 long 上限约束 | 本地已使用 GTO `BigLongSlice`，包含 long 边界与共享输入处理，无需导回先前不受物理上限约束的实现 |
| `c568e675` | JEI 书签测试兼容旧/新接口 | 测试夹具兼容改善，可按需移植；不是新增生产功能 |

本地 `RecipeViewerMixinPlugin` 仍按 JEI/EMI 是否安装选择 Mixin；上游新插件额外按 JEI 15.57 / 15.68 分流。本项目锁定 JEI `15.21.0.144`，且已有通过实际 GTO 测试的转移路径，不能只套用上游版本阈值就认定兼容。应检查真实依赖中的类和方法、EMI/JEMI 接管方式后再决定。

## 不直接移植的内容

- alpha `72bb5978` 恢复 NeoForge 21.1.220 基线，本项目是 Forge 47.4.20，不适用该构建切换。
- 无线 FE 实验分支当前为 `20fa89a6`。其新增 `b9d64065` 减少管理快照的临时字符串，`cd56d196` 增加能源边界测试与服务器基准。当前项目没有对应 `ManagementQuery` / `ManagementSnapshot` 类，属于实验功能前置链，不能单独套用这两个文件的优化。
- 按用户要求，后续上游更新继续排除 NeoECO 兼容与专用测试，不重新引入已清除内容。

## 当前遗留问题

本轮新增终端、提供器公平性及权限修复没有直接修改已知失败对应的时轮替代材料唤醒路径，也没有针对登录阶段超时的修复证据。这两项仍按 [gto.5 测试记录](GTO_NEOECO_REMOVAL_2026-10-21.md) 保留，不能因上游有更新就标记解决。

本轮为源码核查，没有运行新增修复的构建或实机测试，也未改动当前生产实现与制品。
