> 此页是 2026-10-09 移植前的比较快照。后续主线适配和验证见 [2026-10-10 移植记录](forge-1.20.1-feature-ports-20261010.md)。

# 当前 Forge / GTL 工作区与上游对比（2026-10-09）

当前项目与上游 Forge `1.20.1@877672ef` 的文件覆盖已对齐，并额外移植了最新时间轮、供应器 FE、催化器展示和指南更新。但当前项目尚未与上游 Minecraft 1.21.1 的 main/alpha 达到全部功能一致；除了三个独立功能分支，主线本身也有需要另外适配的内容。

此前“上游还有内容”的回答重点检查独立分支。本次补充主线完整文件清单与实际代码核对，不能将此前列出的三个分支视为全部剩余工作。

## 比较基线与方法

实时 `ls-remote` 核对的上游提交如下，均未比前一次检查前进：

| 分支 | 提交 | 用途 |
| --- | --- | --- |
| `1.20.1` | `877672ef` | Forge 同版本源码与资源的完整内容比较 |
| `main` | `5d6c0da6` | 1.21.1 主线完整文件覆盖、最新提交和重点功能核对 |
| `alpha` | `6c7ecf31` | 1.21.1 Alpha 完整文件覆盖及最新修复核对 |
| `feat/large-overload-factory` | `ee16ce3d` | 大型过载处理工厂的独立改动 |
| `feat/celestweave-armor-art` | `fee0ad22` | 功能提交 `522d698d` 的穿戴模型、美术和发光层 |
| `feat/cs-overload-parallel-20261002` | `36a3f089` | AE2CS 联动及该分支的平衡变更 |
| `docs` | `608bc46c` | 设计方案，包含当天的反应堆草案 |

本地以实际工作区为准，包括未提交、未跟踪源码，而非只比较 `HEAD@e381ebc0`。Java 先按路径、再按同源集的唯一类名对应；无线门面类采用显式映射。资源处理 1.21.1/1.20.1 的单复数目录差异。比较区分原始字节、换行、包名、注释、格式及 JSON 排版；不把文件数量转换为功能同步率。

完整扫描包含 26.1.2 的文件清单，但没有逐项审查其平台专用行为。main/alpha 与功能分支采用完整覆盖清单、指定提交差异和重点源码检查，没有宣称对其每一个已存在类完成行为等价证明。

## 上游 Forge 基线

扫描 `src/main/java`、`src/main/resources` 与 `src/main/templates` 共 **3,345 个路径**：

- Java 文件 1,268 个，1,267 个有本地文件对应。
- 唯一没有同名文件的 `RitualItemBurstClientBridge`，由本地 `ClientNetworkPacketHandlers.handleRitualItemBurst` 承担，`RitualItemBurstPacket` 通过 Forge 客户端边界调用它。
- 全部 Forge 生产资源路径均有本地对应；无线范围和连接验证门面也已确认是包路径调整。
- 3,229 个路径按本次自动比较规则一致；115 个路径仍有文本差异。其中 105 个为 Java，10 个为资源。这包括 GTL 适配、新增修复、变量改名和方法位置变化，**不代表缺少 115 个功能**。

本次没有发现整个 Forge 基线模块缺失。文件覆盖和文本检查不替代运行验证，也不能排除已有类中的行为差异。

## 已有功能与修复

| 对比项 | 当前工作区 |
| --- | --- |
| Forge `877672ef` 的样板迁移、接口方向与目标保护、合成卡需求合并和失败重算 | 已按既有移植记录适配；相关生产类和资源存在 |
| `main@5d6c0da6` 时间轮批次容量准入、拒绝/异常原型回滚、空闲供应器单件回退 | 已移植 |
| `alpha@6c7ecf31` 供应器拒收 Applied Flux FE、感应卡输出能源 | 已移植，并为 AE2 15 全局 `isAllowed` 适配 |
| `alpha@e65fa857` EMI/JEI 模式、流体和催化器提示 | 已移植 |
| `alpha@bbb20b0c` 中英文采矿工厂、过载 IO 端口指南 | 已移植 |
| CS 分支的矩阵八倍产量、Pigmee 64 件输出上限 | 本地已具备，不能列为新功能缺口 |
| CS 分支的护甲 20 tick 共享扣费窗口、仅死亡推进连击档位 | 本地已具备；`ShieldChargeWindow` 与该分支的差异是 Java 17 的 clamp 写法 |
| BigInteger 规划/下单、原生 AE2 合成确认菜单扩展 | 已有；不等于已实现主线的新合成报告页面 |
| Useless 可选批次接口与 AE2WT 无线样板编码终端合并 | 已有；不等于已实现下表中的寰宇样板编辑或增强合成终端 |

## 主线中明确需要另外适配的功能

以下缺口通过类清单、实际实现、注册或资源核对，不只依据提交是否是祖先：

| 功能 | 上游实现证据 | 本地状态与界限 |
| --- | --- | --- |
| 天枢合成终端及无线版本 | `TianshuCraftingTerminalPart`、`TianshuCraftingTermMenu`、有线/无线终端物品和配方 | 当前有天枢样板编码终端，缺少这套新的合成终端注册与实现 |
| 终端内置五类工作页 | `TianshuWorkPage`：合成、锻造、铁砧、切石、存储元件；`TianshuWorkstationStorage` 保存真实输入 | 未包含这套工作站；现有样板编码面板不构成等效手工工作站 |
| 新合成报告界面、缺料收藏及强制下单 | `AE2LtCraftConfirmScreen`、`crafting/report/*`、JEI 收藏适配、ExtendedAE Plus 强制下单桥接及报告重算状态 | 缺少新页面和这些操作；本地已有的 BigInteger 重算入口继续存在 |
| 过载合金砧 | `OverloadAlloyAnvilBlock/Menu`、方块模型、掉落和合成配方 | 未注册/实现；需要按 Forge 1.20.1 原生铁砧 API 适配 |
| AE2WT 增强合成终端相关功能 | `TianshuEnhancedWirelessCraftingMenu`、`TianshuTerminalMerge`、工作站合并配方 | 未包含这套增强合成终端功能；本地已有普通无线合成终端与天枢无线样板终端的合并配方 |
| Useless 寰宇样板编辑与配方查看器导入 | `OmniversalPatternDraft`、`TianshuOmniversalEncodingPanel`、`UselessModClientTransfer/PatternBridge` | 未包含编辑和 JEI 导入路径；当前 Useless 批次执行适配仍存在 |
| AdvancedAE 反应室在 EMI 中的工厂工作站登记 | `AdvancedAeFactoryEmiCompat` 的 `EMIReactionChamberRecipe.CATEGORY` 登记 | 缺少这个专用桥接类，需核对 Forge 对应 AdvancedAE/EMI API 后适配 |

自动清单中 main/alpha 各有 75 个 Java 路径没有同名本地对应，但这不是 75 个独立缺失功能：其中有客户端桥接、平台专用 Mixin 和可能由其他类承担的行为。没有把这些未逐项证明的路径全部列成可直接移植任务。

强制下单的差异还涉及实际执行：上游时间轮会读取 `EaepForcedCraftingPlanAccess`，还原原始计划并将 `manualMissing` 加入待输入任务；本地使用原始 `plan` 提交。后续适配需要同时处理服务端任务与缺料等待，不能只添加界面按钮。

## 独立功能分支

| 功能 | 对比结果 |
| --- | --- |
| [大型过载处理工厂](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/tree/feat/large-overload-factory) | 本地缺少 33 个新增生产 Java 文件，包括控制器、结构、仓室、配方执行、资源账本、恢复胶囊、界面和状态包；相关部件、配方和指南也未移植。分支文档说明最终结构为 3×3×3，具备 T1–T4、36/144 槽仓室、CPU/被动模式、苍穹专用档及后续性能优化。上游测试和性能数字没有作为本地验证结果使用 |
| [Celestweave 装甲美术](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/tree/feat/celestweave-armor-art) | 缺少穿戴模型、发光层、专用客户端扩展和新增纹理；现有 Forge 扩展隐藏原始盔甲模型，需要另行适配穿戴渲染 |
| [AE2CS 过载并行卡与母岩联动](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/tree/feat/cs-overload-parallel-20261002) | 缺少并行卡注册/配方、母岩配方、3 个超频辅助类及 5 个 AE2CS Mixin；相关产量和护甲平衡已有，移植时应只补实际缺口 |

## 必须保留的本地差异

| 项目 | 本地实现 |
| --- | --- |
| 平台 | Minecraft 1.20.1、Forge 47.1.47、Java 17、AE2 15.4.10；main/alpha 的 NeoForge/Java 21 API 需要转换 |
| 配套核心 | `thunderbolt-reborn-forge-1.20.1-gtl:2.0.0`，保留规划注入、GTL CPU 让位和容量准入 API；不能按版本号直接换为普通上游构件 |
| GTL 规划与 CPU | 独占规划、Transfinite/超限计算阵列与 ME CPU 部件钩子、闭环任务归属防护、每物理 tick 派发去重及 V2 无节点请求者兼容 |
| 网络 | 协议 `gtl-10` 和既有包序号；不应替换为普通上游协议。客户端与服务端应使用配套构建 |
| 过载接口 | 本地还有队列/展示缓存分配优化、模糊模式缓存、外部回调重入保护、异常退出和自动导出后缓存失效防护 |
| 客户端与存档 | Forge 客户端处理边界、旧 NBT 模块迁移、既有 GTL 菜单和算法选择状态 |

这些差异不能仅为消除文本 diff 而覆盖。接口代码有本地附加优化，但本次没有用相同环境对上游和本地压测，不能据此给出性能领先比例。

## 设计与验证边界

`docs@608bc46c` 的球状闪电反应堆为 v0.1 设计草案，通用多方块框架为后续规划；文档分支明确没有模组实现。它们需要新增开发，不属于现成代码回移。

本次仅比较和整理记录，没有修改模组生产源码、合并、提交或发布，也未运行新的游戏测试或性能测量。最近一次移植验证的 1,463 项单测、6 项批次压力测试，以及 80/77/21 项服务器回归详见[移植记录](forge-1.20.1-upstream-backports-20261009.md)，不能证明尚未移植功能的正确性。

复核工具为 `.codex-tmp/compare-upstream-20261009.py`。完整逐文件结果、源码快照校验值、CSV 和差异位于 `build/upstream-comparison-20261009/`：`comparison.json`、`files.csv`、`diffs/`、`inspected-upstream/`。下载缺失 Git 对象后，指定比较范围内没有无法读取的源码对象；完成时检查了快照中的文件未发生变化。

建议按“主线终端/合成报告与工作站”“大型工厂”“装甲美术与 AE2CS 可选联动”拆开适配；任何步骤都需要保留现有 GTL 规划、资源归属和协议约束。
