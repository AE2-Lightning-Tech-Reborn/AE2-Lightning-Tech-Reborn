# Core 2.0.4-gto.2 GTO 批量派发验收

后续修复版 `2.0.4-gto.3` 的新制品复验见 [gto.3 配套验收](GTO_CORE_GTO3_ACCEPTANCE_2026-10-31.md)；本文保留 `.2` 的原始失败及制品范围。

当前配套验收尚未通过。本文针对成员交付的 Core 优化制品，区分本次补跑与已存在且重新核对过的实跑证据；不以 GameTest 编译成功、专服正常退出或旧 Core 的通过记录替代行为验收。

## 固定制品与环境

Core 源码提交为 `b09da7fdb741d4e96c1d12344735926e5de5b54f`，坐标为
`com.moakiee.thunderbolt:thunderbolt-reborn-forge-1.20.1:2.0.4-gto.2`。
项目 `repo/`、冻结制品与专服实际 JAR 的 SHA-256 均为：

```text
868167da2d511f3136dc6b72b5619e479117fdf656157055d69f1f33850b06c5
```

Minecraft `1.20.1`、Forge `47.4.20`、Microsoft OpenJDK `21.0.10`；Dev23 使用
GTOCore `26.10.10`／AE2 `15.2610.31`，Dev25 使用 GTOCore `26.10.18`／AE2
`15.2610.40`。NeoECO 保留 `20.5.2-gto.1`；原生批量组另装真实 EAEP `1.6.3`，
缺席时预检必须停止，不计为通过。

本次实例采用冻结的 LT 正式包 `2.1.3-gto.1` r9，SHA-256
`358ba472560aa2110a25e9592f880072a85d0a900bd378e6733ad747abbd8f7d`。
已有 r10 夹具包为 `28c4657692e1c863254184f829c522cc541a7ec3d2d0ad7d5e260c0a0fefbe41`，
独立 QA 为 `2efe8c820ade4c291b44a3e80695e263ac225ea664983da0301089e56fe3fd13`。
各轮不得仅凭相同版本号混用结果。

本次修正夹具后的冻结包为
`artifacts/core-gto2-acceptance-server-qa-5ce75f2dbe9d`：附加夹具包 SHA-256
`e2ff72bf219bda87bcfddf36a3781f6f9899420badeaca5e4a0dd16565b5f95e`，独立 QA
`15334b650e63a8d1ecf54c60cba86c955fb517e185a75756b548064e61da2c9e`。
通过原正式包与新重混淆夹具组包，核对正式包全部条目及 2,354 个生产类逐字节一致，
无重复 ZIP 条目；原正式包与 Core 不变。

## 本次补跑及夹具修正

首次 Dev23 原生批量组因缺少 EAEP 被预检阻止，实际执行 0 项。加入真实
EAEP `1.6.3` 并保留旧 GTO 配方缓存后，以新世界执行
`dev23-core-gto2-batch-acceptance-r2`：5 项中 4 项通过、1 项失败，
`startupFailures=0`，JVM 正常退出；驱动正确返回失败。

通过项覆盖部分接收仅退款未接收部分、完整拒收返还全部输入、具体准入限制取料，
以及提交归属不明时停止且不退款、不普通回放。它们实际调用 Core
`BatchExecutor`，供应器与能量服务为协议夹具，不作为完整实体机器覆盖计数。

第五项在 `GlobalBatchAdaptersGameTests.craftingPattern` 查找
`minecraft:oak_planks` 时抛出 `NoSuchElementException`，尚未到达 NeoECO
部分接收断言。GTO 已移除此原版配方；夹具改用独立 QA 已提供的
`ae2lt_qa:oak_planks`，仍为一原木产四木板，保留真实模式编解码、接收数量、
退款、异常归属及全部原断言，不修改 GTO 或 NeoECO 配方。

| 本次补跑 | 实际结果 | 耗时 |
| --- | --- | ---: |
| Dev23 `upstream-batch-native`，`dev23-core-gto2-batch-acceptance-r3` | **5／5 通过**；CPU／STORAGE／PLANNER 全部通过，`startupFailures=0` | 151.0 秒 |
| Dev23 `base`，`dev23-core-gto2-base-acceptance` | **11／11 通过**；包含 3 项实体 GTO CPU、6 项状态同步和 2 项共享种子闭环回归；三个桥检查全部通过 | 71.8 秒 |
| Dev25 `base`，`dev25-core-gto2-base-acceptance` | 注册 11 项，执行共享种子调度时专服崩溃，没有完整逐项结果或汇总；不得记成 11 项已执行或全部失败 | 257.8 秒，含异常停服收尾 |

Dev23 的 NeoECO 分支已实际到达 FastPath facade，验证 8 份中接收 3 份、
返回未接收数 5、模板不变，以及不确定提交异常原样传播且不普通回放。
原失败日志保留；配方修正后的成功没有改写首轮 4／5 结果。

共享种子两项在真实整合包内调用时轮 CPU，使用脚本供应器和测试宿主；
分别验证 `4+3+3` 不均匀批次和 `1+1+1` 单份回退。十次闭环仍只借用并返还
一个种子，普通材料恰好消耗、净产物恰好交付、任务正常关闭。
它们不作为实体 NeoECO FX 机器的共享种子场景计数。

Dev25 共享种子调用栈先到达 Core `BatchExecutor.outputAmounts:522`，再于
`AEKeyLongMap.addTo(AEKey,long)` 抛出 `NoSuchMethodError`。这次是输出语义核对路径，
与原生批量 r10 在 `ParallelBatchCpuHelper.bulkExtract:222` 的取料路径分别证实了
两个受影响类。错误越过 GameTest tick 引发专服崩溃，保存异常后停服卡住；
已保留 crash report、线程转储和停止请求，核对父进程及 JVM 参数后仅终止本次
启动的 Java PID。驱动记录失败及非零退出，未生成任何成功汇总。

### 构建范围

标准 `gtoQaJar gtoQaLtJar` 首次重编译失败于工作树已有的
`JeiCompatibilityClientProbe.java:243`：编译期 EMI 的
`displayRecipeCategory` 接受单个 category，而该客户端探针传入 `List`。
没有修改这个无关探针。临时 init 脚本仅从此次编译排除该客户端类，
全部批量、实体 CPU、时轮与共享种子夹具保留；服务器 QA 构建及重混淆随后通过。
这不是完整客户端 QA 构建通过的结论。

重编译还带入工作树已有的 JEI／Useless 生产改动；与旧正式包比较有 11 个新增类、
3 个变化类。因此这些重编译生产输出没有部署到本轮实例，实际使用上节独立组包并
严格核对的原 r9 正式生产字节码。本次源码改动仅为批量夹具配方定位与本文。

## 已有实跑证据复核

以下均使用上述同一 Core，已核对结果 JSON、实际 JAR 身份及原始日志；本次未将其重复执行次数加入通过数。

| 场景 | 证据 | 结果与边界 |
| --- | --- | --- |
| Dev23 真实 NeoECO F9／FX | `dev23-fx-r10` | 通过；8 主机、88 个物理 FX、`2^100+37` 次精确派发，产物为四倍，原料余量 0，无重复回执 |
| Dev25 真实 NeoECO F9／FX | `dev25-fx-r10a` | 相同业务断言通过；该组合没有 AppFlux |
| Dev25 加装 AppFlux 的 FX 启动 | `dev25-fx-r10` | 在 `FluxKey` 覆盖 AE2 `.40` 的 final `hashCode()` 时失败，未进入机器行为；后续成功不抹除此失败 |
| Dev23 正式包保存恢复 | `dev23-recovery-{prepare,resume,verify}-r10` | 三个独立 JVM 正常保存、退出并打开同一世界；同一任务 UUID，消费 3、交付 3；第三 JVM 确认产物 3、原料 0、处理箱空、任务不复活 |
| Dev25 正式包保存恢复 | `dev25-recovery-{prepare,resume,verify}-r10` | 同样通过三个独立 JVM；制品及世界固定 |
| Dev25 原生批量 | `dev25-upstream-batch-native-r10` | 0／5 通过，均在 Core 旧 Map 方法调用处失败；后续材料结算断言未到达 |

保存恢复覆盖真实实体 GTO CPU、LT 超载任务、无限存储盘和代表外部处理器的箱子，
不覆盖 NeoECO 工作线程本身在运行中的跨 JVM 恢复，也不覆盖写盘过程中断电。
真实 FX 场景与 `GlobalBatchAdaptersGameTests` 的协议夹具分开报告。

## Core 侧仍需处理的边界

实际发行字节码重新核对发现，`ParallelBatchCpuHelper` 与 `BatchExecutor`
仍有 7 处 `AEKeyLongMap.put/addTo(AEKey,long):long` 调用，分别为 4 处
`put`、3 处 `addTo`。AE2 `.31` 提供这些方法；`.40` 改为继承以
`IdentityKey` 为参数的实现，原调用无法链接。修复须同时覆盖七处，保持原生身份键容器、
饱和数量和方法返回语义，并在两版真实 AE2 上验证；不由 LT 修改取料／退款规则绕过。

此前同一 Core 的完整 LT JUnit 为 1,872 项：1,851 通过、7 失败、14 跳过；
独立实际 API 审计为 20 项：12 通过、8 失败、0 跳过。七项 NeoECO
契约失败在两组中重复，不是十四个独立缺陷；默认回调错误阻断的后续提交断言仍须重跑。
另一个 ABI 边界为 Core `IdOnlyInputGroups$DemandKey` 和 `LateBoundOutputKey`
覆盖 `.40` 的 final `hashCode()`，已由实际规划及字节码检查证实。

Core 修复后应交付新的明确版本、完整源码提交、正式发行 SHA-256 与本地 Maven
坐标，避免覆盖同一 `.2` 坐标。LT 侧再固定新制品复测双版本原生批量、共享种子、
NeoECO 协议、真实 FX 与保存恢复。当前结论不支持宣布 `.2` 全部 GTO 环境验收通过。

## 复现与证据

本项目既有隔离测试根目录为
`D:/CodexTaskData/ae2lt-gto-upstream-port-20261031`；原始日志、逐进程 JAR
身份及 JSON 位于其 `server-results/`，旧缓存保存在实例的
`.staged-artifacts-history/`。本次二进制复核及构建记录位于工作区忽略目录
`.gradle/core-gto2-acceptance-20261031/`；开发环境日志、存档和 QA JAR 不加入提交。
汇总为该忽略目录的 `acceptance-summary.json`，含各轮原始收据／日志哈希、
新旧结果归属和实际终止记录。两个实例的本次测试 JVM 均已结束。

```powershell
.\gradlew.bat gtoQaJar gtoQaLtJar -PgtoQa `
  -I .gradle/core-gto2-acceptance-20261031/server-qa.init.gradle `
  '-Pthunderbolt_local_repository=E:/mods/Thunderbolt-Core-GTO/repo' `
  '-Dnet.minecraftforge.gradle.check.certs=false' --offline --no-daemon --max-workers=2
```

固定并部署实际 QA 制品后，用隔离驱动选择 `upstream-batch-native`（必须为 5 项）
和 `base`（必须为 11 项）。记录实际注册数、逐项结果、CPU／STORAGE／PLANNER
检查及非零汇总；不能只检查退出码。保存恢复用正式 LT 包及独立 QA，
`restart --phase prepare|resume|verify` 连续启动同一世界。
