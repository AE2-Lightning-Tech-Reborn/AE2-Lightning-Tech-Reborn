# GTL 时间轮全局批量注册接入修复（2026-11-08）

本次修复通过定向验收：原 `globalAdapterRunsThroughActualTimeWheelCpu` 使用 **Core 全局注册入口**完成三层矩阵链，原始数量与状态断言全部通过。全量 JUnit **1966/1966**、批量压力测试 **6/6**、原矩阵回归 **8/8**、`check`、`build` 与完整发行包检查通过。Core 沿用冻结的 `5ca4da0`，未修改或重建。

这关闭了此前“AE2LT 时间轮无法命中 Core 全局批量适配器”的失败。整包仍为 **NOT_FULLY_ACCEPTED**；本报告不关闭先前记录的其他缺陷或未验收项。

## 修复行为

非空 `explicitAdapter` 按 Core 原有契约替代全局查找。AE2LT 原先传入本地 `withRegistered(OPTIONAL_BATCH_ADAPTER)`，未组合 Core 全局表；存在非空 fallback 时，全局注册因此不可见。

时间轮改用物理 CPU 持有的 `BatchProviderAdapterContext`，查找顺序为：

1. 原生 `IBatchCraftingProvider`。
2. AE2LT 本地注册，保持原插入顺序。
3. Core 全局注册，保持 Core 的优先级降序、ID 升序。
4. 原有可选适配 fallback。

现有 `withRegistered` 公共方法继续保留本地注册语义。Core 的显式覆盖契约未改变。新全局入口排除已知的 `neoecoae:*` 和 `thunderbolt:neoecoae` 注册，不新增或恢复 NeoECO 适配、依赖、注册、配方或夹具。

上下文保留各 resolver 的缓存策略，按提供者和 resolver 的对象身份缓存命中及未命中。声明仅缓存当前 tick 的 resolver 在下一 tick 刷新；声明跨 tick 缓存的 resolver 在提供者修订、服务身份、fallback 或注册快照变化时失效。没有修订接口的服务按 tick 失效。每个缓存 endpoint 在首次使用的 tick 调用 `beginDispatchTick`，同 tick 重访复用；不缓存的 resolver 继续逐次解析。普通 adapter 每次获得当前 pattern 与借用的 job view，不缓存任务或样板引用。

缓存有界：最多 128 个 resolver、每个 resolver 最多 512 个提供者，超限按最近使用顺序淘汰。池内虚拟 CPU 共享同一物理 CPU 的上下文；独立 CPU 各自持有上下文，保留执行防重入保护。

## 源码与制品

基线提交：`2f5b810ad8199a9cf0847e1bddd9ddcd121c5cf5`。隔离源码仅叠加本次五个实现/测试文件，没有带入工作树原有的迁移夹具改动。验证前后核对这五个文件与隔离源码一致，并校验原有修改和 AGENTS.md 的哈希未变。

| 文件 | 作用 |
| --- | --- |
| `src/main/java/com/moakiee/ae2lt/compat/BatchProviderAdapterContext.java` | 新增注册组合与独立 resolver 缓存 |
| `src/main/java/com/moakiee/ae2lt/compat/BatchProviderAdapters.java` | 提供本地不可变注册快照 |
| `src/main/java/com/moakiee/ae2lt/crafting/timewheel/Ae2LtTimeWheelCraftingCpuLogic.java` | 将上下文传入实际批量执行路径，保留既有重载 |
| `src/main/java/com/moakiee/ae2lt/crafting/timewheel/TimeWheelCraftingCpuPool.java` | 同一物理 CPU 的虚拟 CPU 共用上下文 |
| `src/test/java/com/moakiee/ae2lt/compat/BatchProviderAdapterContextTest.java` | 新增 9 项注册与生命周期测试 |

完整双包在 `build/gtl-global-adapter-fix-20261108/pair/`，同目录附 `.sha256`：

| 制品 | SHA256 |
| --- | --- |
| `ae2lt-forge-1.20.1-gtl-2.1.3-beta1.jar` | `080aa9ebb18b195cfe7f17433520482e468dc37fdc1aec977f0d9e7daa0a4cdb` |
| `thunderbolt-forge-1.20.1-2.0.4.jar` | `1bd40221c927b9fc5b26dd1e08fc1ba3f3feb5f7c701e9d23e208793dde4868c` |

发行检查核对 ZIP 完整性、新实现 class、JarJar 元数据及其内嵌 JAR、许可证与模组元数据，确认无本次 QA/JUnit 夹具。运行前后双包和整套 mods 哈希一致。

## 验证结果

编译及 JUnit 使用 Java 17、Forge 1.20.1 和冻结 Core 的映射缓存，可选 API 审计开启。`test` 的 1966 项含新增 9 项，0 失败、0 错误、0 跳过；`adaptiveBatchStress` 的 6 项单列。新增测试覆盖本地/全局/fallback/原生优先级、Core 注册排序、缓存命中与未命中、跨 tick 策略、同 tick 注册/注销、服务及提供者修订、CPU 隔离、不缓存 resolver、可变 job view 与不同 pattern。

生产专服使用 Minecraft 1.20.1、Forge 47.4.16、GTLCore 1.2.3.2-fix1、GTCEu 1.4.4、AE2 15.4.10 和默认 Radium 0.12.4。Radium 配置 SHA256 保持 `b87ee52fdc722ce1497e7ff55fdbd0660fc9df40c8f2f8167465cab55a388f0d`。

直接复用原 `TianshuDeferredReturnsGameTests`，仅为避免与发行包产生模块包名冲突而迁移 Java package。测试方法体、Core 全局注册 API、模板与全部断言不变；未改用 AE2LT 本地入口，未下调数量或超时。

| 原始回归 | 结果 |
| --- | --- |
| `globalAdapterRunsThroughActualTimeWheelCpu` | PASS：三次批量派发、6 梯子、2 余料木棍、无 CPU/矩阵输出滞留 |
| `threeLayerBatchChainCompletesWithoutAdvancingTick` | PASS：同 tick 完成三层链 |
| `sharedClosedLoopSeedReturnsBetweenBatchesInSameTick` | PASS：共享种子在下一批次前返还 |
| `concreteSeedSinglePushReturnsAfterItsLedgerCommit` | PASS：具体种子在账本提交后返还 |
| `storageCallbackCannotReenterDispatchOrDrain` | PASS：拒绝存储回调重入 |
| `ordinaryPushPathAlsoCompletesInOneTick` | PASS：普通推送保持八次计费 |
| `lastDispatchBudgetStillDrainsItsReturn` | PASS：末次预算仍排空返货 |
| `blockedFinalOutputSurvivesSaveAndReload` | PASS：阻塞输出经 NBT 恢复后恰好交付 |

该 JVM 正常 `save-all flush` 并停止，退出码 0，LDLib 目标异步保存异常和链接错误计数均为 0。约 87 秒包含整包启动、测试和退出，不能作为算法性能测量。

这组夹具使用真实矩阵端口、控制器、合成逻辑、时间轮 CPU 和原版配方；网格、存储与能量服务是测试替身。它验证全局注册经实际生产派发路径的修复，不替代真实 GTL 批量机器或有限能量验收。NBT 用例在同一 JVM 中重建 CPU，不冒称跨 JVM 的活动订单恢复。

## 证据与未关闭项

本轮本地证据位于 `build/gtl-global-adapter-fix-20261108/`：`preparation.json`、`pair.json`、`full-r1-result.json`、`outputs/test-results/`、`fixture-source.json`、`fixture-build-r1-result.json`、`qa-runs/original-chain-r1/report.json`、`qa-runs/original-chain-r1/process.json` 和 `result.json`。生成脚本、运行日志、辅助包及世界保留在忽略的 build 目录，不加入提交。

此前冻结测量已结束后才构建本修复；旧包与旧失败证据保持原样。本轮没有重跑全套 100 项整包矩阵、客户端 GUI 或导入导出性能，不把之前的结果归入新包验收。原 CPU 列表大数同步冲突、闪电/闭环撤销间歇失败、断网返货确认，以及真实 GTL 批量机器、有限能量、多人和长期稳定性等未验收范围继续保留，详见此前全面测试报告。

本次只修改上述五个实现/测试文件并新增本报告，保留工作树原有报告及迁移测试改动。按 GTL 移植约定整理为一个本地汇总提交，不推送。
