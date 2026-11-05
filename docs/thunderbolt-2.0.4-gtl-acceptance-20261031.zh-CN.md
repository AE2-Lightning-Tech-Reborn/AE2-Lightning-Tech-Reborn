# Thunderbolt 2.0.4 完整 GTL 验收（2026-10-31）

**结论：NOT_FULLY_ACCEPTED（更新至 2026-11-02）。** Core 摘要修复已独立复测 4/4 通过；本轮 AE2LT 移植异步 AE 节点保存修复后，GameTest、正式 GTL 冷启动/同存档重启及四组长请求均通过，原 LDLib 目标异常未再出现。整包仍保留导出卡顿比例 **0.1667% > 0.1%** 的历史失败，有限供能、实际批量机器等未验收项仍未关闭。

原初验完整 Core 包 SHA256：`b63453d02b6acb41117eb5980b2d2e49e3c67b65189be19e4fcd04e5c6379cc4`。本次摘要修复复测使用 Core 本地汇总提交 `3d18f68` 对应的冻结完整包，5470110 字节，SHA256 **`efb0d63bc6fb2ab81f0778ab9f6792ec853468ddb61a1e90f6b0693b1639e06f`**；已核对交接附带的 SHA256 文件。两包版本号均为 2.0.4，按哈希区分。

原初验及 Core 摘要修复两轮配套 AE2LT 2.1.3-beta1 均为 `17b78166e26c360acda2b89eb1e4050647a5827a70153dacff09083c0a88c7de`，来自本工作区既有移植工作树的初验重建；源文件清单在原证据目录。Java 17.0.20、Minecraft 1.20.1、Forge 47.4.16、AE2 15.4.10、GTLCore 1.2.3.2-fix1、GTCEu 1.4.4、Radium 0.12.4、EAEP 1.5.4.1 + GTLAdd 1.0.3。原有 NeoECO 20.5.1 保留，没有恢复被排除的上游适配或夹具。

AE2LT 原初验 JUnit **1949/1949**、批处理模型 **6/6**，check/build 及完整包静态审计通过。开发编译使用原 Core 的映射副本，正式运行使用原始发行包字节。以下客户端重启、存储持久化、完整合成守恒和 I/O 数据均来自原 Core 初验；2026-10-31 的 Core 摘要修复轮只复测原四组有料长请求规划/摘要，不将这些历史通过记为修复包重新验收。2026-11-01 新 AE2LT 产物的实际验证另列于后文。

原初验客户端保留 92 份 JAR；除配套 Core/AE2LT 外其余 90 份逐一核对。原验收世界 192 文件作为种子。Radium 配置只有默认说明，SHA256 `b87ee52fdc722ce1497e7ff55fdbd0660fc9df40c8f2f8167465cab55a388f0d`，没有关闭 NBT 规则。原四次成功运行的 debug.log 均记录 Radium NbtCompoundMixin 与 Core CompoundTagCopyMixin 实际应用，均未见 NoSuchMethodError、NoClassDefFoundError、VerifyError 或 Mixin apply failed；不把其他整包既有错误说成日志干净。

## 原 Core 的历史真实运行

| 场景 | 结果 |
| --- | --- |
| 正式客户端冷启动 | PASS_CHECKPOINT，四轮输出 4/64/16/32 木板，取消 256 任务后材料守恒，空闲 200 tick 库存不变；正常保存退出 0 |
| 同世界独立 JVM 重启 | PASS，冷启动的 99 原木保留，无完成任务复活；重启后制作 4 木板，剩余 98 原木；正常退出 0 |
| GTL 菜单与规划器 | 实际请求 2147483652 无 int 截断，Crafting Tree/GTL/原生摘要保留；重算与关闭清理通过；原生和 V2 均执行真实有料合成 |
| 专服冷启动/重启 | PASS，PID 不同，同世界正常保存/退出；原客户端包中的 Oculus 与两份 Jecharacters 在专服 CONSTRUCT 失败，三份仅在专服副本移出并保留原字节 |
| 真实无限存储单元跨 JVM | 四键数量 2147483652/333/444/555 保留；普通/NBT/两份实际 Forge capability 载体保持隔离；模拟只读、真实提取/回存守恒、嵌套 long 数组深拷贝通过 |

Forge capability 用例在隔离辅助模组中给纸附加真实可序列化能量能力（11/22），验证的是实际 Forge capability 身份与持久化契约，不代表已覆盖某台 GTL 机器的具体能力实现。辅助结果的 `cellId` 字段使用字符串 getter 读取 UUID，显示为空；不据此宣称验证了 UUID 字符串，恢复判据为实际单元、四个键及完整数量。

## 摘要修复包的独立 long 复测

**FIXED_RETEST_PASS，4/4。** 新场景 `handoff-core204-summary-retest-r1/native-io-runs/long-range-fixed-r1` 从原 192 文件种子另建世界，独立正式专服 JVM（PID 76772，production=true）运行。与原 `long-range-r8/process.json` 逐项核对 89 个 JAR，仅替换 Core；其余 88 个 JAR、配套 AE2LT 和原重混淆探针完全一致。探针 SHA256 为 `280d56ee38a258abe1b4009a1d4cd7b0fc3a7a5ff35e56041ffa396915ff3d3b`，未重编译或修改断言、配方、超时与预算；运行器只调整输入/输出路径、替换 Core 并增加制品核验，差异保存为 `runner.diff`。

| 实际后端 | 请求数量 | GTL 样板执行次数 | 仅预览 | 库存不变 | 结果 |
| --- | ---: | ---: | --- | --- | --- |
| ae2:vanilla | 2147483652 | 2147483652 | false | true | PASS |
| ae2:vanilla | 9223372036854775807 | 9223372036854775807 | false | true | PASS |
| thunderbolt:v2 | 2147483652 | 2147483652 | false | true | PASS |
| thunderbolt:v2 | 9223372036854775807 | 9223372036854775807 | true | true | PASS |

GTL 按交接确认的契约显示样板执行次数；本场景实际整包石头→石按钮配方为 1:1。V2 极值仍为 `simulation=true`、`previewOnly=true`、`incomplete=false`，石头 stored/used 与按钮 crafting/finalOutput 均为 `9223372036854775807`，missing=0；精确 byte cost 保持 **`27670116110564327437`**，long 投影为 Long.MAX_VALUE。实际 `gtlcore$getCraftTimes()` 从原来的 `0` 恢复为 **`9223372036854775807`**；Crafting Tree 负载和原生摘要断言均通过。这里只验证规划和摘要，没有提交极值执行请求，仍保留精确预览边界。

修复包运行的 Radium 配置哈希与原值一致，debug.log 再次记录 NbtCompoundMixin 与 CompoundTagCopyMixin 应用。原断言全部通过后 `save-all flush`、正常 stop，70.09 秒退出，exit=0、All dimensions are saved；采集时确认所属 JVM 已不存在。生产源码、原运行器、原失败结果、种子存档、源配置以及既有工作树/暂存区均通过前后哈希核对。

## 原 long 失败记录与 I/O 历史结果

原完整包的有料 long 规划为 `FAIL`，原 `results.json`、`long-range-r8/process.json` 和原始日志保持不变。原生后端的超 int、Long.MAX_VALUE 与 V2 超 int 三组通过；V2 Long.MAX_VALUE 的库存、用料、产物和精确成本均完整，但 GTL 摘要次数实际为 `0`，未满足 `9223372036854775807` 断言。本次仅将该缺陷更新为已修复并独立复测通过，不覆盖历史失败。

V2 极值按 Core 既有边界进入 ExactPlanPreview。原初验核对冻结 Core 字节码后，夹具显式验证 complete、previewOnly 和超 long 成本，保留全部数量/库存断言；没有调用 exact 执行 API。此前缺料组的原生规划器通过 Long.MAX_VALUE 请求/缺料/GTL 摘要次数；早期夹具要求 V2 必定可执行的失败也原样保留。本次直接使用原最终探针，未再次调整预期。

以下 I/O 为原 Core 的历史结果，本次摘要修复未重跑性能。原负载为 1024 个目标、27 个物品键/槽，200 tick 预热、1200 tick 采样、3 GiB 堆，包含 eligibility，关闭额外 diagnostics；独立 JVM 和新世界，使用正式包及整包默认 Radium。辅助模组将原基准方法的所需成员原样复用，只做包名迁移和读取三个包内观察入口；每个真实服务端 tick 推进一次夹具，不手动驱动机器 CPU。补充标准 pack.mcmeta 和强制加载夹具区块后重测，未改变负载、预计数量、timeout 或原预算。

| 正式专服场景 | 正确性 | 样本 | MSPT mean/P99 ms | I/O mean/P99 ms | 原预算 |
| --- | --- | ---: | --- | --- | --- |
| export-control | PASS | 1200 | 1.063726/9.7935 | 0.472289/9.0578 | control |
| export-stress | PASS | 1200 | 7.357807/15.872 | 4.674366/9.8474 | FAIL |
| import-control | PASS | 1200 | 1.052323/4.4578 | 0.143204/1.1523 | control |
| import-stress | PASS | 1200 | 6.94009/18.0807 | 3.544173/8.7168 | PASS |

吞吐与卡顿原始结果：

- export：稳态完成 1351680/1351680 个消费事件，最小窗口/目标吞吐 1.0/1.0；全夹具消费 2402942976 物品，缓冲余量 0；>50 ms tick=2/1200（0.001667），GC=27 ms，采样堆峰值=2801084632 B。
- import：全夹具实际/计划产量 2385248256/2406481920，网络导入 2385248256，最终余量 0；>50 ms tick=1/1200（0.000833），GC=21 ms，采样堆峰值=2743107816 B。

预算使用原 `checkWirelessIoBenchmark`：mean/p95/p99 MSPT ≤25/35/45 ms、>50 ms 比例 ≤0.001、GC pause ratio ≤0.02、推导 capacity TPS ≥19.8、相对 control 的 mean/P99 增量 ≤10/15 ms、I/O P99 ≤12 ms，要求实际 FAST 调用与完整 1200 样本。预算日志保留全部超限项。capacity TPS 为 MSPT 推导值。每方向一组控制/压力配对，不提供统计意义的版本提速结论。工作站为 i5-13400（10 核/16 线程）、约 32 GiB RAM；原初验曾观察到其他 Java 构建任务，未连续记录所有正式样本期间的后台负载，没有终止第三方进程。

JFR 分配结果在 results.json 的每场 allocation 中：记录整个 JVM 的 ObjectAllocationSample 事件、服务端线程及 I/O 栈的抽样分配权重。包含启动、预热和末尾排空，不是正式 1200 tick 窗口的精确分配总量，也没有旧 Core 的同整包配对分配基线。

## 2026-11-01 AE2LT 移植与异步保存修复

普通 Forge `1.20.1` 已适配至 `2df9c5b5`，新 AE2LT 完整包 SHA256 为 **`78bc91886b7341b574f78744d5868efa9bcfbde3b3ffd340280da260952ce632`**，版本仍为 2.1.3-beta1；配套 Core 为上述 `efb0d63...` 冻结包。本轮还修正了资源过滤未指定 UTF-8 导致的发行元数据损坏，失败制品和审计保留，修复后另行冻结并通过完整发行审计。

本轮完整 JUnit 1949/1949、模型 6/6；异步保存 GameTest 4/4 required 通过，包含普通 AE 节点、已有 FE/AE/隔离余额与未决提取、同 tick 拆分合并预算的三个新增回归。资源修正后的默认 build 再跑 test 为 1939 通过、10 项可选 API 门控跳过，不与前一完整套件重复相加。

正式 GTL 独立副本仅替换 AE2LT/Core，其他 87 个 JAR、存储探针、默认 Radium 和原 192 文件世界种子不变。冷启动 PID 99184、同世界重启 PID 36800 均 PASS，四键数量 2147483652/333/444/555、普通/NBT/真实 Forge capability 身份和只读模拟全部通过，正常保存退出 0。独立新世界中的原四组长请求也 4/4 PASS（PID 86816）：V2 极值仍为完整仅预览，实际 GTL 次数为 Long.MAX_VALUE，精确成本 `27670116110564327437`，库存不变，正常保存退出 0。

这三次正式 JVM 的 `Server thread required` 与 LDLib `asyncThreadLogic error` 均为 0；结合新增行为回归，本轮将已知异步节点保存问题记为**定向修复验证通过**，原先异常日志不改写。客户端交互、长时间负载、有限能量及真实批量机器未在本轮重跑，性能历史失败继续保留。

本轮报告：[普通 Forge 1.20.1 内容移植至 GTL](forge1201-gtl-port-20261101.zh-CN.md)；证据为 `build/port-forge213-gtl-20261101/` 中的单测 XML、`node-serialization-r1`、`dedicated-cold-r1.json`、`dedicated-restart-r1.json`、`native-io-runs/long-range-ported-r1/` 和 `release-ae2lt-r2/`。

## 保留的失败与边界

第一次客户端尝试被辅助监视器误杀：ModernFix 用 FATAL 级别输出 Redirector 兼容性提示；该完全相同文本也出现在旧版已正常保存的日志中。只在新监视器副本中精确区分这条提示，实际 FATAL/Mixin 失败、正常退出检查和超时规则仍保留；三个监视器回归检查通过，旧失败和原种子哈希保留。客户端重启的 GTL JEI 预览计算曾耗时较长，保存了线程栈；最终没有提高超时且正常通过。

仍失败或未完成的验收项：

- 有限供能环境中的合成能量逐项对账；现有完整合成场景使用创造能量单元，只证明材料/产物守恒。
- 真实 GTL 催化剂、膨胀样板和实际 EAEP 批量设备的提交/执行/取消守恒；已测 CPU 合成不替代这些机器路径。
- 独立客户端的菜单返回按钮数量保留；已覆盖真实 GTL long 菜单入口、摘要、重算和关闭清理。
- 导出原预算的 >50 ms tick 比例为 2/1200（0.1667%），高于 0.1%；本次未重跑或关闭此失败。
- 同一完整整合包的旧 Core 配对性能/分配基线；原初验测绝对原预算，不据此声称版本提速或无相对回退。
- 当前整包 Useless 不含 AlloyFurnaceBigIntegerProvider；BigInteger/Smart Doubling 新公开协议和真实机器执行未测。

## 修改范围与证据

原初验只新增本工作区内的隔离验证脚本、辅助探针、复制实例、冻结制品和本报告。本次更新本报告，并在 `handoff-core204-summary-retest-r1` 新增准备/采集脚本、原运行器的路径调整副本、冻结修复包和原探针副本、新世界/进程日志与结构化证据。生产源码、依赖属性、AGENTS.md 与既有未提交修改保持原状；没有提交、合入或推送，也没有修改 Thunderbolt Core 项目或原启动器实例/原存档。

证据入口：`D:\CodexBuilds\ae2lt-gtl-validation-00afc43fdf984ecf93dbcfb7087df4fd\build\port-forge-213-gtl-20261031\handoff-core204-acceptance-r1`：`results.json`、`unit-summary.json`、`client-summary.json`、`dedicated-*.json`、`native-io-runs/`、`native-queue-r3.json`、JFR、原始失败日志与 `preservation-final.json`。冻结 AE2LT 在同级 `handoff-core204-release-r1/`，客户端运行在 `handoff-core204-client-r1/`。本报告不提交开发环境日志。

本次复测证据入口为本工作区 `build/port-forge-213-gtl-20261031/handoff-core204-summary-retest-r1/`（沿用既有 build junction）：`summary.json`、`provenance.json`、`evidence-index.json`、`runner.diff`、`process-closure.json`、`preservation-before.json`、`preservation-final.json`，以及 `native-io-runs/long-range-fixed-r1/` 下的 `process.json`、`native-io-result.json`、`console.log`、`game/logs/debug.log` 与 JFR。报告更新前后副本分别为 `acceptance-report-before.zh-CN.md`、`acceptance-report-after.zh-CN.md`，差异见 `report.diff`；历史汇总 `handoff-core204-acceptance-r1/results.json` 未改写。


## 2026-11-02 最新 Core 双包直接实跑

使用 Core `5ca4da0`（SHA256 `1bd40221c927b9fc5b26dd1e08fc1ba3f3feb5f7c701e9d23e208793dde4868c`）与 AE2LT 最新已合入生产代码 `7a5ccc2a` 对应冻结完整包（SHA256 `94c0fcad3e075bb060d9c14ee0bdb991f8f981b5aea91e298faa25d870fe0c5f`），独立完成默认 Radium 客户端/专服各自冷启及同世界新 JVM 重启，均正常保存退出 0。四个 JVM 的四键数量与身份持久化均通过；客户端实际有料原生/V2 合成和取消守恒探针通过。

本轮 JUnit 1957 通过、0 跳过，模型 6/6，check/build/发行审计通过；真实 ElementalCraft 6.0.1 场景的 EJECT/附魔、异步保存及规划优化合计 11/11，原四组长请求 4/4。六次最终成功运行的目标 LDLib 异常及相关 ICCE 未复现。早期辅助场景失败保留并单列，不算产品通过。完整结果、SHA256、进程与证据入口见[最新双包直接实跑报告](gtl-dual-direct-20261102.zh-CN.md)。

本轮实跑范围通过，整包总状态仍为 NOT_FULLY_ACCEPTED：导出历史预算失败以及有限供能、真实批量机器、返回按钮、旧版本基线和 Useless 新 API 等边界继续保留。生产源码和原有未提交修改未改，报告在本地汇总，未推送。
