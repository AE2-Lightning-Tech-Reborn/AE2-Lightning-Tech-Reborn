# Core 算法续办第②步：LT 消费契约与增量验收

核对 LT 固定源码 `6135dcc2110d3b745682b854ce013e10c22583d5`。未发现 Core `.4`
要求 LT 修改公开接口的差异：LT 发行包引用的 **110 个 Core 类型**在 `.3` 与 `.4`
中字节一致；两版真实 AE2 输入下的 **351 条调用引用（207 个不同成员）**均可静态解析。
本步保留 LT 的 NeoECO 集成，不修改生产源码、Core 依赖锁或 PR #54。

本结论是后续 Core 算法修复的兼容基线。新算法制品尚未交付，本步没有其运行、正确性或
性能结论。此前 `.3` 的完整 GameTest、FX、保存恢复、图形和多人结果仍归属于原制品。

## 固定输入

目标为 Minecraft `1.20.1`、Forge `47.4.20`、Microsoft OpenJDK `21.0.10`；LT 使用现有
Gradle `8.8` wrapper。NeoECO 固定参考源码为
[`381e3f844beca250b0f824017f0fe585c9364144`](https://github.com/Yang120231/NeoECOAEExtension/tree/381e3f844beca250b0f824017f0fe585c9364144)
（GPLv3），源码版本 `20.5.5-gto.1`；只核对协议和行为，没有复制实现或夹具。

| 输入 | SHA-256 |
| --- | --- |
| LT 已验收发行包 `2.1.3-gto.1` | `dc84960f69c6d75f1a28298b8c76b3b509ac0d7b53e87a8d3bd0aa285e74e664` |
| LT 生产锁定 Core `2.0.4-gto.3` | `1ebff68ab9a2c7e041b8ca8338acb1a00aa9f5eacfa6604b4f55f2cd04be85a9` |
| Core 对照基线 `2.0.4-gto.4` | `734d9bf152a66dee5bf69397106b6808a64305969459ba60c2e8f7139c08c7cd` |
| AE2-GTO `15.2610.31` | `f63917bf5a2ca0692c0550ad2d045991f7a493d89021bfb30b51026d4ad31a3d` |
| AE2-GTO `15.2610.40` | `b0d97544e618d73ec0c8d8e54df36351bf7f98f7e1984c6455d81658b6320b5c` |

Core `.4` 坐标为 `com.moakiee.thunderbolt:thunderbolt-reborn-forge-1.20.1:2.0.4-gto.4`。
本轮直接从 Maven Local 核验 `.3`、`.4`，从此前冻结的实际运行输入核验 AE2 两版，未重新下载。
NeoECO 二进制审计使用实际 `20.5.2-gto.1`，与参考源码版本分别记录，不能互相替代。
其哈希、类级清单、调用索引和各阶段结果见[机器基线](docs/baselines/lt-core-algorithm-20261102.json)。

LT 输入使用之前验收的冻结包；本机 `build/libs` 的同名包 SHA 不同，但逐一核对的全部
**2370 个 LT 类**相同。本步始终对冻结包审计，不以同名构建输出替换它。
Core 契约依据为第①步提交 `607c33e11374a7a49cb1a94bf28beeb2bb58ff83` 中的
`docs/GTO_CORE_ALGORITHM_CONTRACT_20261102.zh-CN.md` 及配套 JSON，性能 before 源码为
`bd510967eb1f9a376d80e74695bef27bafbaa5dd`。

## LT 的实际边界

| LT 入口或消费者 | 必须保留的语义与配套检查 |
| --- | --- |
| `BigCraftingMenus`、`TianshuPlanningServiceMixin`、`TianshuPlanningBoundaryTest` | 通过 `ICraftingPlanningService.resolve()` 取得选择；天枢 CPU 存在性和 vanilla opt-out 保持一致。LT 不自行把 `DECLINE` 改写成确定无解。 |
| `BigCraftingService.preview`、`BigGraphExport` | 在采集前建立同一个绝对 `nanoTime` deadline（3 秒），采集和异步 worker 都绑定同一 `PlanningAttemptContext`；队列等待不重置预算。取消通过 checkpoint/线程中断和截止约束，尚未在本步证明端到端取消时延。 |
| `ExecuteLoopPattern`、`ClosedLoopExpandedPatternDetails`、`LoopSeedLedgerBook` | 保留查找/执行解包的区别、完整 AEKey、种子组 UUID、物理库存竞争、槽映射、派发优先级和持久化定义。共享单种子不产生第二份物理库存。 |
| `Ae2LtTimeWheelCraftingCpuLogic.runBatchOnly` | Core 负责原生批量派发和真实接收量结算；LT 传入借用的可重绑 job view、预算与保留种子。回执返回未接收份数，不能反向解释。 |
| `TimeWheelBatchInputAllocation` | ThreadLocal 仅在同步作用域、同一 pattern 和 inventory 身份下替换取料；异常恢复外层作用域，其他 CPU 和重入调用保留原行为。 |
| `BigProviderDispatcher`、`BigCraftingJob` | prepare 不转移 escrow；按真实回执结算，未确定提交保留 in-flight 归属并停止。已接受部分不能退为本机库存；返还不足继续有保管者。 |
| LT `NeoEcoCraftingCompat` 与 Core `NeoEcoGtoBatchAdapter` | 借用模板需复制、prepare 捕获 UUID；缩量重新准备；一次 submit；容量只是建议；默认回调、CPU 一次扣料扣能、普通 UUID 回退及不确定异常传播保持原约定。两个桥不能重复取得材料/能量结算权。 |
| NeoECO exact、迁移和生命周期反射 | `prepareExactAllocated`、exact `craftCount/submit`、`cancelJob`、`ECOCraftingJobLifecycle.finish` 和 `PatternMigrationCoordinator` 的工厂/所有者/释放签名必须存在；实际依赖 JAR 新增检查已通过。 |
| `ExactPlanReports`、确认页、状态表 | 请求、执行和显示保留精确数量；报告不能把 UNKNOWN/取消当成确定缺料或把可行结果标为最优。新制品仍需客户端验证。 |

无必须新增的 LT 公开接口。若 Core 后续发现共享签名或所有权协议确需修改，按第①步的
共享变更流程先更新契约，再做 LT 配套；不能仅因本步静态检查通过就修改语义。

### 需要额外保留的内部耦合

1. `TimeWheelBatchInputAllocationMixin` 指向 `BatchExecutor.runBatchOnly` 的具体重载，并要求其中
   恰好一个 `ParallelBatchCpuHelper.bulkExtract` 调用点。只保留公开方法签名不足以保持该注入。
2. `TimeWheelInputExtractor` 反射使用 `BulkResult(KeyCounter[], long, KeyCounter[], KeyCounter[], List)`
   和 `RemainderSpec(AEKey, long, boolean)` 构造器。布局变化会关闭 LT 原生批量出口，不能把
   安全降级理解为仍完成批量验收。
3. `TianshuPlanningServiceMixin` 还 Shadow 私有 `grid:IGrid`，在 `resolve` 返回时限制选择；
   `CraftingCalculationReportMixin` 注入 `GtoPlanningCalculation.call` 的 HEAD/RETURN。
4. `NetworkCraftingSimulationSnapshotMixin` 在 Core 库存快照构造器拦截
   `MEStorage.extract` 和 `IStorageService.getCachedInventory`，供报告使用原库存/减量库存。

新审计直接读取冻结 LT 包内的 Mixin 注解，核对上述 **4 个 Core 目标、7 个锚点/Shadow**。
这些是第③/⑤步优化时需要检查的实际消费者；移除或搬移调用点时需 LT 配套和实际 Mixin 加载验证。

## 本步验证与可复用入口

| 检查 | 结果 | 范围 |
| --- | --- | --- |
| `coreConsumerAudit` | **6/6**，无失败/跳过 | 四份原始发行 JAR 显式路径和 SHA；两版 AE2 各解析 110 类型/351 调用引用；93 条 LT→Core 继承关系的抽象要求及 final 约束；反射布局、Mixin 锚点、缺成员/静态性/接口种类的负向控制。 |
| `.4` `optionalApiAudit` | **22/22**，无失败/跳过 | 13 项实际二进制检查、1 项真实 NeoECO 接口发现、7 项隔离协议夹具、1 项加载制品身份检查。包含两版 AEKey final 方法约束及本次新增 exact/生命周期/迁移检查。 |
| `.4` LT 定向 JUnit | **102/102**，无失败/跳过 | NeoECO 借用快照、订单身份、缩量、默认回调、未知提交；原生取料/拒收退款、共享种子、作用域恢复、派发预算、槽位/账本/优先级、exact 入口和报告数量。 |

协议夹具的实际 Core 来源与二进制审计输入路径必须相同，否则检查失败。本轮加载的映射包为
`thunderbolt-reborn-forge-1.20.1-2.0.4-gto.4_mapped_official_1.20.1.jar`，SHA-256
`d7c04dc8718161d3e1f2fd12564c6212d53df459006976d5b30e56700ce15ee0`。
这是 ForgeGradle 映射后的测试输入，不能替代上表原始发行 SHA。

定向 JUnit 使用开发环境 AE2 `.31`，由现有 test agent 应用 GTO 身份约束；`.40` 在本步是
真实字节/API 检查。本步未执行 `.4` 的整合包、独立服务端、客户端、多人或跨 JVM 恢复。
静态解析覆盖声明描述符、普通调用、字段及方法句柄，使用实际 Core/AE2/JDK 祖先；不初始化
目标类，不等于完整 JVM 验证器或 Mixin 变换，也不自动推断任意反射字符串和可选插件行为。

原始 XML、日志、冻结输入和导出索引保存在忽略目录 `build/core-contract-step2-20261102/`，
不提交环境日志。可移交的调用索引为[TSV](docs/baselines/lt-core-consumers-20261102.tsv)。

复用字节审计时运行现有 wrapper，传入 `-I scripts/core-consumer-audit.init.gradle coreConsumerAudit`，
并为 `lt`、`core`、`ae2Dev23`、`ae2Dev25` **每项**提供
`-PcoreAudit.<id>.jar=<原始发行包>` 与 `-PcoreAudit.<id>.sha256=<预先确认的SHA>`。
缺少显式输入会失败；该任务不改变生产依赖。实际例子已存于忽略目录的 `run-audit.ps1`。
不要在省略哈希时按目录首个同名前缀自动选包。

执行候选的 API/协议检查时，在测试命令中显式覆盖 `-Pthunderbolt_version=<候选版本>`，
使用 `-I scripts/optional-api-audit.init.gradle -PoptionalApiAuditMods=<实际可选模组目录> optionalApiAudit`。
本轮使用 `.4`；新制品交付后改为第⑦步确认的坐标，并重新核验原始 SHA 和实际加载映射包。
本次 102 项选择由机器基线中的 `targeted_test_selectors` 完整记录。

## 第⑧步增量验收用例（待新制品）

以下各项都需记录同一候选坐标/发行 SHA、LT 提交、实际加载类来源、AE2/整合包版本及回执。
不把本步结果提前标成新候选通过。Core 第③步的独立 oracle/库存顺序回放及第⑤步守恒与性能
证据仍由 Core 交付；LT 负责真实消费者边界，不能用其中一层代替另一层。

| 编号 | 可复用入口/场景 | 必须观察的断言 |
| --- | --- | --- |
| A1 | 原始发行身份、`coreConsumerAudit`、`optionalApiAudit` | SHA、包元数据与第⑦步一致；两版 AE2 的调用、继承、反射和 Mixin 锚点通过；用实际发行包启动 Forge 后无链接/注入失败。 |
| A2 | 天枢规划开关、无解/UNKNOWN/取消、exact 预览 | 同一绝对截止覆盖采集、排队、搜索、认证、回退；UNKNOWN/取消不形成确定无解/成功缓存，已认证可行结果不冒充最优；不阻塞 Grid 长循环。 |
| A3 | `ClosedLoopSharedSeedBatchGameTests`、真实天枢多任务共享种子 | 缺种子不启动；两个逻辑池竞争同一物理种子不超借；同 ID 不同 NBT 键分开；不丢槽映射、优先级、消费者 UUID；不均分批和单份回退均关闭订单。 |
| A4 | `GlobalBatchAdaptersGameTests` 的五项批量场景 | 实际准入限制取料；完全拒收全额退款且不扣能；部分接收只结算一次；未知提交停止且不退款/普通重放；可选 adapter 保持 UUID 回退。 |
| A5 | `.4` 的 102 项定向选择和 NeoECO 协议夹具，换为新候选 | prepare 后重绑 view/修改借用计数器，再缩量提交仍使用原 UUID/快照；默认回调正确，CPU 与外部桥不重复扣料或扣能；一次准备不跨 tick 复用。 |
| A6 | Core 新增退款不足/异常回归在 LT 原生取料出口的对应场景 | 材料、催化剂、能量各账本守恒；回存拒收或仅收一部分时，余量仍有唯一保管者；取消/后续 tick/拆除不造成再次退款、丢失或重复掉落。若实际协议无法表达回执，交回具体缺口，不能假定回存成功。 |
| A7 | Dev25 `NeoEcoGtoGameTests`、`UselessBigIntegerGameTests`、混合 provider/report/request-routing 场景 | 超 long 数量不截断；真实输入、escrow、in-flight、费用、输出和请求 UUID 一致；exact 预览/确认/启动/取消跟踪完整。 |
| A8 | Dev23/Dev25 各 prepare→退出→resume→退出→verify（三个独立 JVM） | 同一订单 UUID；已接受工作、共享种子、未交付产物及待退款余量各恢复一次；不足回存的状态若由本轮修改，必须新增对应真实保存场景。 |
| A9 | 两版真实 FX；变动涉及菜单/报告时追加图形与两真实玩家确认/取消/重连 | 原生与 LT 都能确认、运行、关闭订单；数量与进度精确；共享任务不串玩家/订单，重连不重提；保存截图/网络回执，不用无界面测试替代。 |

候选预计使用新的未占用版本（第①步规划为 `.5`），以 Core 第⑦步实际交付为准。
本步不提前发布、替换 `.3` 锁、重写旧验收报告或推送本地过程提交。
