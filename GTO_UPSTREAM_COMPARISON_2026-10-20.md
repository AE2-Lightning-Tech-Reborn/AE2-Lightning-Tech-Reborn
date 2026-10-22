# 当前 GTO 工作树与上游对比（2026-10-20）

已联网更新 LT 和独立 Core 的上游引用，比较对象包括未提交的生产代码、测试和适配。没有合并代码、重置工作区或替换正式制品。Core 操作仅在 `D:/codex-builds/thunderbolt-infinite-port-20261020` 中进行。

结论：当前项目已包含上一轮 IO 修复、无限存储优化和主要 Forge 1.20.1 功能；最新 1.21.1 上游还有值得移植的修复，但不能整分支合并。最新主分支也没有提供足以解释并解决本轮所有测试失败的现成改动。

## 对比基准

| 仓库 / 分支 | 本次抓取的提交 | 说明 |
| --- | --- | --- |
| LT 本地 `gto` / `upstream/gto` | `cb80004f` | 两者提交差距为 0 / 0；本地另有未提交修改 |
| LT `upstream/1.20.1` | `2228757b` | Forge 1.20.1 通用版本 |
| LT `upstream/main` | `256e76ca` | MC 1.21.1 / NeoForge，多方块预览改造 |
| LT `upstream/alpha` | `42ae4a42` | 含自由视角修复和无限盘摘要优化 |
| Core 远端 `gto` | `7c7a066` | 本地独立移植目录的原始 GTO 父基线 |
| Core `upstream/main` | `6caa5aa` | MC 1.21.1，ECO 交接修复和 beta3 版本标识 |
| Core `upstream/alpha` | `91af245` | 合入当前 main |
| Core `upstream/1.20.1` | `a49351a` | Forge 通用规划与批量实现 |
| Core `upstream/1.20.1_GTL` | `fdd766c` | 已移植无限盘优化及后续维护重构 |

LT 本地为 `2.1.2-gto.2` / Core `2.0.3-gto.2`，继续固定 Forge 47.4.20、GTOCore 26.10.5 和内嵌 AE2-GTO 15.2610.8。

## 已包含的上游内容

提交历史显示本地相对 `1.20.1` 为本地独有 2 个、上游独有 7 个提交，但代码核对表明这些提交的主要内容已被集中移植，不能把数量当成功能落后程度。

| 上游提交 / 内容 | 当前工作树核对 |
| --- | --- |
| `2228757b` 接口导出调度 | 提交涉及的 3 个源码/测试文件与本地一致，按换行规范化核对 |
| `ebad1649` 供应器派发和回料快照 | 提交涉及的 11 个源码/测试文件与本地一致 |
| `d8a04bf4` 原生终端精确库存 | 已有对应实现；9 个源码/测试文件中 5 个一致，其余包含 GTO 菜单/终端适配及后续修改 |
| `e8e36595` 混合原生供应器精确订单 | 已有对应实现；17 个源码/测试文件中 11 个一致，其余保留 GTO 兼容差异 |
| `f5196281` NeoECO 测试 | 对应测试已存在，包含 GTO 夹具适配；本轮真实 GTO 未安装 NeoECO，两项跳过，不能算实际附属集成通过 |
| `ff79e2f9` EMI 最低版本 | 本地已为 `[1.1.22+1.20.1,2)` |
| `4c61ffb7` 多方块布局和顶部端口 | 已有对应实现；12 个源码/测试文件中 10 个一致，其余含 GTO 适配 |
| `7c76557f` IO 空槽重载修复 | 已移植并保留 Forge NBT 签名，上一轮空槽回归由失败变为通过；本轮 IO 40 项通过 |
| `42ae4a42` 无限盘摘要和回归 | 本地此前从 `19fe1d8b` 移植。新提交的 `InfiniteStorageCellItem` 和 `InfiniteCellRegressionGameTests` 两文件与旧来源之间无差异，无需按新哈希重复移植 |

这里的“一致”指文件内容，不表示测试全部重新执行。带差异的文件依照已有移植记录和本轮实机覆盖作出判断，不声称逐行等同。

LT 无限盘当前直接读取 Forge NBT，保留 `ae2lt:types`、`ae2lt:bytes` 和 `ae2lt:cell_id`。新版 alpha 用于 NeoForge 的摘要组件/Codec 不适合照搬。当前 main 尚未包含 alpha 这次摘要变更，因此这部分本地已有 main 尚未合入的修复语义。

## 最新可移植内容及顺序

| 优先顺序 | 上游提交 | 当前差距及建议 |
| --- | --- | --- |
| 1 | LT alpha [`609606f9`](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/commit/609606f930ef91f71c7fc8d094eff04c936530dd) | 修复自由视角实体共享玩家 UUID / `isLocalPlayer()` 时被相位锁控制。当前仍使用 `player.isLocalPlayer()`，缺少上游 `ClientAccess` 和 `Minecraft.player` 对象身份检查。适合单独移植四个生产文件并适配 1.20.1 Mixin 注入点，再验证真实玩家与相机各自行为；这是源码确认的缺失修复，尚未在本轮环境复现自由视角故障 |
| 2 | LT [`7417bd90`](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/commit/7417bd90b7b476d51ff0607aea4e1dac7952b518) | 统一时间轮/大数派发中的重入保护、每 tick 预算及延后回料；新增候选索引和缺料唤醒。当前缺少 `CraftingDispatch`、`CraftingTickBudget`、`RecipeCandidateIndex`、`CraftingMaterialReadiness` 等新实现。可移植核心调度，需保留 GTO CPU 桥、long 分片和 NBT 语义；Useless Mod 公开精确 API 作为可选集成另行验证 |
| 3 | LT [`c987d8c6`](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/commit/c987d8c68a7139ee5889cc0d2eb467856c2d851e) + Core [`9cb676b`](https://github.com/AE2-Lightning-Tech-Reborn/Thunderbolt-Core-Reborn/commit/9cb676b9a3d78a9cb4539c3f9603cb227b9bb6c7) | 补齐 ECO 普通派发回退的订单 UUID、FX 精确准备/回料、取消通知及外部 CPU 过载认领 API。当前有旧的可选 ECO 兼容路径，但缺少新的 `NeoEcoBigIntegerCompat` 及这组公开交接接口。需配对移植 LT/Core，保留旧 ECO 降级路径，并使用目标 Forge ECO 版本验证；当前包未安装该附属，不是立即可验证的默认 GTO 修复 |
| 4 | Core [`f77f0cb`](https://github.com/AE2-Lightning-Tech-Reborn/Thunderbolt-Core-Reborn/commit/f77f0cb74a7430e48317d31dfc4b573a1b2471c3) | 无限盘快照维护重构：新增 `CellNbtSnapshot`、`ExactCellSnapshot`，聚合通道状态并集中缓存失效。本地已移植此前的 long 快速路径、快照隔离及单位变化重算，但没有这两个新辅助类。可参考已完成 Forge 适配的 `1.20.1_GTL`，继续保留本地有限容量、126 位边界及异常存档修复；重构收益不能代替数量守恒回归 |
| 5 | LT [`256e76ca`](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/commit/256e76cad4171cea378a9c314fdbb260e406be85) | 多方块预览改为 LDLib2 Scene。当前自有预览在正常启动 EMI 测试已通过；上游新增代码直接依赖 `com.lowdragmc.lowdraglib2`，当前 GTO 是不同的 LDLib API。可参考布局/交互思路，不宜直接替换已验证实现 |

`main` 相对上次参考点 `7c76557f` 涉及 59 个文件；`alpha` 相对该点涉及 56 个文件，两者有重叠，不能相加成待移植文件数。

无线实验、Pigmee 工作站及 MEST 仍是单独功能分支，本次没有把这些实验内容列为默认 GTO 必须合入的修复。

## 上游构建配置的具体风险

`256e76ca` 把 NeoForge 提升到 `21.1.262`（最低 `21.1.238`），使用 JEI `19.64.0.455`、LDLib2 `2.2.42`，与当前 Forge 1.20.1 / JEI 15 环境不兼容。

同一提交还把 LT/Core 版本和依赖要求从 beta4/beta3 改回 beta3/beta2，改用 CurseForge 的 Core beta2 取包；而它的祖先仍包含需要新 Core API 的 ECO 修复。这里只确认配置发生了回退，尚未构建上游验证它是否会触发链接错误；不能据此照搬本地版本号或降低配套 Core 要求。

上游 `JeiWirelessSupplyMixin` 新开关针对 JEI 19.27+ 的迁移。本地运行 JEI 15 且已有 GTO 转移适配，不能原样套用版本阈值。

## 与本轮失败的关系

| 本轮问题 | 上游代码对比结果 |
| --- | --- |
| `TerminalBatchRecoveryTest` 预算断言失败 | `TerminalBatchRecovery.java` 与 Core main、1.20.1、1.20.1_GTL 完全一致（忽略换行）。该失败用例方法体也相同；未发现上游已经提供的直接修复 |
| `PlanningCandidateExecutorTest` 两项取消/隔离断言失败 | 两个用例方法体与 Forge 1.20.1/GTL 一致。main 的一个用例只在平台线程创建 API 写法上不同；生产 main 使用虚拟线程、本地 Forge 使用守护平台线程，本地还已有 `CancellationException` 处理修复，不能用覆盖 main 文件来解决计时不稳定 |
| QuickPlay 下 EMI 重复注册 `gtceu:liquefaction_furnace/extract_air` | 新上游预览提交不修改该 GTOCore/GTOLib 配方注册入口，没有证据说明移植预览即可修复；正常菜单 EMI 通过记录仍与 QuickPlay 失败分开 |
| 修正测试 UUID 后登录超时 | 本次 LT/Core 新提交没有足以定位该登录失败的证据；不能把新合成派发或预览移植当作已证实的解决方案 |

本次为源码与提交比较，没有重新运行游戏或上游完整测试，也未声称上游在相同 GTO 环境稳定。当前测试结果仍以 [全面测试报告](GTO_COMPREHENSIVE_TEST_2026-10-20.md) 为准。

机器可读核对：`D:/codex-builds/ae2lt-gto-realtest-20261019/upstream-comparison-20261020/comparison.json`。包含抓取时间、完整提交哈希、逐提交文件一致性和失败用例方法体对比。


本次对比后的实际移植、Forge/GTO 适配取舍及验证结果见 [最新上游移植记录](GTO_LATEST_UPSTREAM_PORT_2026-10-20.md)。本文件中的待移植状态保留为对比时的历史快照。
