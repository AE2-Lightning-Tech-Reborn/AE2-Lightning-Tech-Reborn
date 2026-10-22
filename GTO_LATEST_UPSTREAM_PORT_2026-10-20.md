# 最新上游移植（2026-10-20）

本文保留 gto.3 的历史结果。当前 gto.4 的新增内容、配套成品与验证见 [2026-10-21 移植记录](GTO_UPSTREAM_PORT_2026-10-21.md)。

配套版本：LT `2.1.2-gto.3` / Thunderbolt Core `2.0.3-gto.3`。维持 MC 1.20.1、Forge 47.4.20、GTOCore 26.10.5 和内嵌 AE2-GTO 15.2610.8。客户端、服务端须一起替换两个主 JAR。

## 来源与行为

| 来源 | 本轮内容 |
| --- | --- |
| LT `609606f9` | 本地玩家身份按 `Minecraft.player` 实体引用判断；相同 UUID 的自由视角实体不再继承本体相位移动和飞翼授权。 |
| LT `7417bd90` | 普通与大数 CPU 共享派发事务和重入保护；返回物料在结算后交付；同 tick 预算不能被重复调用重置；缺料任务仅在相关库存足量时唤醒，动态输入保留有界刷新。候选索引保留原顺序、完整匹配和修改版本检查。 |
| LT `c987d8c6` / Core `9cb676b` | 外部超载产物登记、模拟/实际回料和任务取消接口；可选 NeoECO 普通派发保留 UUID，超出 long 范围时可探测公开 exact API。提交异常保留未明输入归属，不重试或虚构退款。 |
| Core `f77f0cb` | 独立精确数量快照与 NBT 写出快照；简化通道统计状态；保留 GTO 有限容量、损坏存档修复、重复键合并和通道单位变化后的重算。 |

旧版 Forge NeoECO 的批量回执兼容继续保留，普通回退也经过带 UUID 的公开钩子。Useless Mod 2.4.6 的大数接口通过反射可选接入；未安装或旧版本仍使用原有派发路线。本轮环境未安装具有这些新版接口的可选附属，不能宣称其真实机器已验证。

## Forge/GTO 适配

- 保留 GTO 已有存档、暂停、普通提供者回退和真实投料/回料修复；未导入 NeoForge 构建配置、JEI 19 或上游的版本回退。
- 模组加载、GameTest、NBT、样板输入输出数组和 CraftingPlan 原始整数映射使用 Forge/GTO API。`BigCraftingTransfer` 使用 LT 已有 `compat/gto/BigLongSlice`。
- Forge fastutil 的 `ensureCapacity` 是私有方法：NBT 载入时按已知大小构造标准新映射，并恢复缺省值 -1。快照仍独立克隆键映射。
- 上游 `256e76ca` 的 LDLib2 Scene 预览未迁移：GTO 使用 LDLib1，命名空间和 Scene API 不兼容。本轮保留现有 JEI 15/EMI 交互预览，不引入 LDLib2 或 JEI 19 专用 Mixin。
- 新增调度、索引、同 tick 预算、缺料唤醒和自由视角回归夹具仅进入可选 QA 包。项目原本默认打包的 `gametest` 类维持现状。

## 构建与源码

Core 独立目录：`D:/codex-builds/thunderbolt-infinite-port-20261020`。同级正在开发的 Core 目录未修改。

Core 累计补丁（历史本地文件 `docs/patches/latest-core-gto.patch`） 包含 16 个文件，基于已保存的 GTO 开发快照 `ba3496623b3510a44ba0b4047b40f0499721a984`，覆盖上一轮无限盘移植及本轮变更，不能再叠加旧 `infinite-core-gto.patch`。基线索引的正向应用、当前源码的反向应用检查均通过。该快照以 upstream/gto `7c7a066` 为父，含此前已有改动；普通上游分支不能直接应用本补丁。未提交或推送本轮改动。

从公开 `upstream/gto` 的 `7c7a066` 重建时，使用 Core 完整补丁（历史本地文件 `docs/patches/core-gto.3-from-upstream.patch`）（72 个文件），其中包含独立快照原有的 GTO 修复及本轮全部改动。它与上述增量补丁二选一，不要叠加。公开基线正向检查及当前源码反向检查均通过。

先在独立 Core 目录运行 `check jarJar verifyReleaseJar publishMavenJavaPublicationToMavenRepository`；LT 构建传入 `-Pthunderbolt_local_repository=D:/codex-builds/thunderbolt-infinite-port-20261020/repo` 以及实际 GTOCore 本地 JAR。QA 加 `-PgtoQa gtoQaJar gtoQaLtJar`，必须等待所有 reobf 任务完成再部署。

## 验证

LT `check`：1518 项，1513 通过、5 跳过、0 失败。Core `check`：1548 项，1535 通过、13 跳过、0 失败（主测试 1525 项，另有 23 项冷启动回归）。使用原有规划器时间阈值与 D 盘 native 临时目录，未放宽超时；历史版本出现过时间敏感失败，本次成功不表示该问题已永久修复。

Core 新增公开钩子的缺失 API 回退、单次普通派发、接受/拒绝、异常不重试和反射 exact 提交回归。发布主 JAR 校验及本地 Maven 发布通过；LT 正式/QA 打包及重混淆通过。

实际 GTO 整包服务端回归 **88 项通过、1 项跳过、0 项失败**；各组的 CPU、STORAGE、PLANNER 启动探针均通过，游戏进程均正常退出：

| 分组 | 通过 | 跳过 | 本轮日志 |
| --- | ---: | ---: | --- |
| 调度、候选索引、同 tick 预算、缺料唤醒 | 19 | 0 | `runtime-r5/server-dispatch.log` |
| 精确数量、混合提供者、三层同 tick 回料、天枢延迟回料 | 17 | 1 | `runtime-r7/server-exact.log` |
| 基础兼容 | 11 | 0 | `runtime-r7/server-base.log` |
| 无限存储回归 | 1 | 0 | `runtime-r7/server-infinite.log` |
| 超载 IO | 40 | 0 | `runtime-r7/server-io.log` |

精确组跳过的是需要真实 NeoECO 的原生批量回执用例。监督脚本在该次启动时仍预期 18 项通过，将跳过项计入预期，因而原 `runtime-r7/server-results.json` 的 exact 标记为 false，监督进程退出 1。实际服务端日志记录 `PASS total=17 failed=0 startupFailures=0`、17 条独立测试通过、一条跳过及三个探针通过，进程退出 0。原始记录未改写；`final-results.json` 根据原始日志及退出状态独立核对，并明确保存这项计数修正。监督脚本的后续预期已改为 17。

真实客户端回归：

- 自由视角：本体继续阻止外力，允许自身移动；同 UUID 的独立 LocalPlayer 和远程玩家可正常移动。`runtime-r6/freecam/result.txt` 为 PASS，客户端退出 0。环境没有 Tweakerge，未宣称与该模组实际集成通过。由于测试玩家未绑定雷电网络，临时将 passive/flight/phaseFlight 的 HV 每 tick 耗电设为 0；真实 T3 能量模块正常支付 FE。测试结束后恢复原配置 1/2/8。这项测试覆盖身份与移动保护，不覆盖雷电网络供能。
- EMI：正常主菜单等待后进入隔离世界，实际配方注册、配方界面、控件渲染边界、旋转、缩放及外壳切换通过，客户端退出 0。结果为 `runtime-r7/emi-results.json`；截图 `runtime-r7/emi-multiblock.png` 已查看。

早期运行的模板命名、Forge 提供者/配方夹具、QA 重混淆部署时序和测试装备供能问题已调整后重跑；早期失败日志保留，不计为通过。历史 QuickPlay 配方重复注册、多人登录稳定性及其他模组资源错误未在本轮解决；上述结果不代表整合包日志没有错误，也不覆盖长期多人压力或所有旧存档迁移。

所有本轮原始日志、备份与机器统计位于 `D:/codex-builds/ae2lt-gto-realtest-20261019/latest-port-20261020`；汇总为 `final-results.json`，单元测试 XML 归档为 `junit-results.zip`。移植前 LT/Core 工作树分别备份为 `lt-before.zip` 和 `core-before.zip`。已有 `GlobalBatchAdaptersGameTests.java` 与移植前备份逐字节一致；混合提供者测试在保留原用例的基础上合并上游新增用例。

## 配套制品

主 JAR 集中于 `build/paired-release/`，配套校验文件为 `SHA256SUMS-gto.3.txt`。正式游戏只使用两个主 JAR，不放入 QA 辅助包或 instrumented 测试包。历史 gto.2 制品与校验文件保留。

| 制品 | SHA-256 |
| --- | --- |
| `ae2lt-forge-1.20.1-2.1.2-gto.3.jar` | `27e982aab741f4cb028a011c6cd50b8eadeab5c0352126fe05048effadf4bb36` |
| `thunderbolt-reborn-forge-1.20.1-2.0.3-gto.3.jar` | `93f84729934b703577ad76e4ac9bc39c1bf095c01a4842fe50ed248a45729aee` |

冻结的 LT 成品采用最后一次正式构建；它与运行前配套目录中的版本仅 `META-INF/MANIFEST.MF` 的构建时间戳不同。正式包 4,211 个非 manifest 文件与本轮 instrumented 包逐字节一致；新增 QA 夹具不在正式包内。精确 Core 版本约束 `[2.0.3-gto.3]` 及 RecipeSearch 归属说明存在，核对记录为 `artifact-verification.json`。

历史补丁仅作为本地归档保留，不随当前版本提交。重建当前配套版本请使用 [已清除 NeoECO 的 Core gto.5 完整补丁](docs/patches/core-gto.5-from-upstream.patch)；不要叠加历史补丁。
