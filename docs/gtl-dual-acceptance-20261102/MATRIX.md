# 本轮验收矩阵

轮次 `gtl-dual-20261102`。执行前按 [PLAN.md](PLAN.md) 冻结源码/双包/环境；本文件第①步全部初始状态为 **NOT_RUN**，没有产品通过结论。第③步给出脚本/用例准确名称、命令、断言和原预算路径；不自行扩大旧整包验收范围。下表 REQUIRED 是本轮通过门禁；各条内部用例均需单列真实结果，不以零测试退出 0 计为 PASS。

状态：PASS（已执行且全部断言满足）、FAIL（执行后断言/运行失败）、BLOCKED（缺依赖/资源等不能运行）、NOT_RUN（尚未执行）、SKIP（明确门控的可选子用例，需理由）。REQUIRED 缺依赖或跳过不能算通过。每行记录 attempt、源提交、原包/配对哈希、环境清单哈希、命令参数、结果计数和本地证据路径，参照统一模板。

## 必需检查与责任

| ID | 关口/执行人 | 范围 | 必须满足的断言与证据 | 初始状态 |
| --- | --- | --- | --- | --- |
| R01 | ② Core；③ AE2LT | REQUIRED 源码冻结及保全 | 开工复核 `1.20.1_GTL` 完整提交、差异范围；隔离构建不含未提交内容；既有文件前后哈希/状态；版本及许可证/NeoECO排除范围不被恢复 | NOT_RUN |
| R02 | ② Core | REQUIRED 完整必需单测、构建、发行审计 | 按仓库配置运行 test/check/assemble/verifyReleaseJar（核对任务图含独立生命周期/超时套件）；JUnit XML 按 pass/fail/error/skip 分列；完整重混淆包及校验；不混入 GameTest 类/资源 | NOT_RUN |
| R03 | ② Core | REQUIRED EJECT 与既有 GTL GameTest | `tools/eject-compatibility-game-tests.gradle`、`tools/gtl-compatibility-game-tests.gradle` 按最新已合入入口核对；接口 super 调用、能力回退、投影身份/面限制、在线/离线/注销、防递归及摘要契约；真实注册/执行数与正常退出 | NOT_RUN |
| R04 | ⑤ AE2LT；② Core 支持 | REQUIRED 真实 ElementalCraft 共存世界加载 | 真实 ElementalCraft JAR/依赖版本与哈希明确；最终双包下实际创建或载入世界、触发附魔/相关调用和 EJECT 行为；无 `must be Methodref constant` / 相关 ICCE；夹具结果单列，缺真实依赖记 BLOCKED | NOT_RUN |
| R05 | ③ AE2LT | REQUIRED 环境与运行准备 | 双端/QA 清单、配置、辅助包、复制存档及种子清单；默认 Radium 保持；原实例不改动；脚本入口、断言、超时和预算有来源，世界与进程隔离 | NOT_RUN |
| R06 | ③核对，⑤ AE2LT | REQUIRED GTL API 与确切 Core 解析 | 逐项提供者/调用方/签名清单；①最新规划入口也纳入；最终编译及 test/runtime 解析到④已审 Core；隔离缓存、原包→映射包映射可追踪，无旧同版本误用、NoSuchMethodError/NoClassDefFoundError/VerifyError/Mixin失败 | NOT_RUN |
| R07 | ⑤ AE2LT | REQUIRED 完整必需单测、模型、构建与发行审计 | 当前仓库 test/check/build 及现有必需批处理模型；真实可选 API 门控逐项列出；Java 17/元数据UTF-8、JSON/TOML、Mixin/refmap、嵌套依赖、许可证、夹具不泄漏；最终原包哈希 | NOT_RUN |
| R08 | ⑤ AE2LT | REQUIRED 异步保存相关 GameTest/运行 | 沿用已有无能量节点、既有 FE/AE/隔离余额与未决提取、同 tick 拆分合并预算等用例；线程契约和状态保存满足；真实运行中目标 `Server thread required`、LDLib `asyncThreadLogic error` 未复现 | NOT_RUN |
| R09 | ⑤ AE2LT | REQUIRED 最新规划优化回归 | 对 `7a5ccc2a…` 及开工后新增差异做定向复核：普通/长请求独占规划归属一致、无天枢默认边界、共享替代槽/局部缺口/数量边界/只读库存与缓存失效、既有穷举 oracle；现有5项相关 GameTest须有逐项回调结果；不声称真实多方块机器已验收 | NOT_RUN |
| R10 | ⑤ AE2LT | REQUIRED Radium/NBT边界 | 冻结默认 Radium，不关闭 NBT 规则；版本/配置哈希、实际 Mixin应用证据；嵌套 long 数组深拷贝、模拟只读、普通/NBT/capability身份不混淆；相关链接/注入异常未复现 | NOT_RUN |
| R11 | ⑤ AE2LT | REQUIRED 客户端冷启动→正常退出 | 最终原始双包，独立客户端目录，复制种子世界并进入可操作世界；数量/身份/只读断言，保存完成与正常退出；实际 PID/启动时间/加载包哈希、世界路径/结果/退出码 | NOT_RUN |
| R12 | ⑤ AE2LT | REQUIRED 客户端同世界独立 JVM 重启 | R11保存后旧JVM确已退出，新JVM启动标识不同、使用R11同一世界路径；持久化数量/身份相符，无任务复活/重复数量；再次正常保存退出 | NOT_RUN |
| R13 | ⑤ AE2LT | REQUIRED 专服冷启动→正常退出 | 独立专服目录/复制世界；双端差异明确；成功世界加载和数量/身份/只读断言；save-all flush/stop或等价正常保存退出、退出码与日志证明 | NOT_RUN |
| R14 | ⑤ AE2LT | REQUIRED 专服同世界独立 JVM 重启 | R13旧JVM退出后新JVM使用R13同一世界；启动时间/PID、保存/读取证据；数量/身份保持，无重复任务/材料；再次正常保存退出 | NOT_RUN |
| R15 | ⑤ AE2LT | REQUIRED 大数存储和身份持久化 | R11–R14各端冷启/重启核对：超int数量 `2147483652` 及历史四键 `2147483652/333/444/555`；普通、NBT、两份真实可序列化 Forge capability 载体隔离；实际提取/回存守恒、模拟不写；记录操作前后与重启后值，夹具能力不冒充机器能力 | NOT_RUN |
| R16 | ⑤ AE2LT | REQUIRED 四组长请求摘要/只读规划 | 下表 L1–L4全部满足，实际后端/GTL次数/库存前后/精确成本/Crafting Tree载荷均留证；不提交极值执行 | NOT_RUN |
| R17 | ④代码审查；⑥代码审查 | REQUIRED 两次只读审查 | ④针对②Core；⑥针对最终双包源码、API/依赖解析、哈希、完整必需证据及旧问题；明确pass/reject，issues可执行；源/包/配置变化按计划使对应旧审查失效 | NOT_RUN |
| R18 | ⑦ AE2LT；⑧任务分配 | REQUIRED 字节交付/闭环 | 独立目录两个完整JAR逐个SHA256=最终受测双包；清单/证据索引/分项报告/两次有效审查/本地可获取路径齐备；缺失项不填PASS | NOT_RUN |

历史脚本用于定位，执行成员须核实本轮已合入源码中的真实入口和必要依赖；脚本迁移只调整本轮路径与制品绑定，原断言/超时/预算和失败保留。完整套件之间有重叠时分别报告，不叠加测试数；缓存命中不冒称实际重跑。可选依赖缺席的协议/结构测试与真实机器未测分开说明。

## R16：固定四组请求

沿用既有真实 1:1 石头→石按钮配方和四组探针契约。先证明配方/材料/输入与历史一致；无法建立相同夹具记阻断，不改预期来消除失败。四组均为**只读规划/摘要**，请求不提交执行。

| 子项 | 实际后端 | 请求数量 | GTL样板执行次数 | 已有契约与数量断言 | 初始状态 |
| --- | --- | ---: | ---: | --- | --- |
| L1 | `ae2:vanilla` | 2147483652 | 2147483652 | 无int截断；stored/used/crafting/finalOutput/missing沿用原探针；库存不变 | NOT_RUN |
| L2 | `ae2:vanilla` | 9223372036854775807 | 9223372036854775807 | 原生摘要及完整数量保持；库存不变；不调用极值执行 | NOT_RUN |
| L3 | `thunderbolt:v2` | 2147483652 | 2147483652 | 完整规划与摘要；库存不变 | NOT_RUN |
| L4 | `thunderbolt:v2` | 9223372036854775807 | 9223372036854775807 | `simulation=true, previewOnly=true, incomplete=false`；stored/used/crafting/finalOutput=Long.MAX_VALUE，missing=0；精确 byte cost=`27670116110564327437`，long投影=Long.MAX_VALUE；库存不变 | NOT_RUN |

## 旧问题保留表（不是本轮新增必需测试）

以下从旧报告保留，⑥/⑧须检查仍单列。即便本轮全部 REQUIRED 通过，也不自动关闭这些状态。

| ID | 旧状态 | 事项与既有边界 |
| --- | --- | --- |
| H01 | FAIL / 本轮不重跑完整性能验收 | 导出 >50ms tick为2/1200=0.1667%，高于0.1%；原性能预算保持，不将本轮启动通过解释为性能修复 |
| H02 | NOT_RUN | 有限供能中的合成能量逐项对账；创造能量材料守恒不能替代 |
| H03 | NOT_RUN | 真实GTL催化剂、膨胀样板、EAEP批量设备提交/执行/取消守恒 |
| H04 | NOT_RUN | 独立客户端菜单返回按钮数量保留；本轮生命周期不代替该交互验收 |
| H05 | NOT_RUN | 同完整整合包旧Core配对性能/分配基线；不声称提速比例或无相对回退 |
| H06 | BLOCKED/NOT_RUN（沿用旧缺依赖） | 真实Useless新公开API、BigInteger/Smart Doubling及真实机器；结构/协议夹具不替代 |

旧失败原日志/结果不可覆盖；历史已修复LDLib异步保存及长请求摘要作为本轮R08/R16的回归目标，须新证据。旧整包总状态继续 `NOT_FULLY_ACCEPTED`，另给本轮状态：PASS / FAIL / BLOCKED / INCOMPLETE，并列出每个失败、阻断、未执行和可选跳过的责任人、原因及下一关口。
