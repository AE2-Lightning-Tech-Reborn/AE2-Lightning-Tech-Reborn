# Core／LT 最终 R6 直接联测

LT 本轮 **69/69 GameTest、6/6 图形／TCP 多人、两版真实 FX、两版各三个独立 JVM 保存恢复通过**。
全部使用最终 R6 生产类，补齐此前 R5 图形验证与 R6 服务端退款修复之间的制品边界。
Core 联测首轮另有一项取消监视器测试超时；原预算独立复跑通过，首次失败仍保留，根因未确定。

本次 PR 更新新增本报告及[机器基线](docs/baselines/lt-core-gto5-joint-retest-20261103.json)，
生产／测试源码、依赖版本和固定二进制保持不变。此前完整验收见
[A1–A9 报告](GTO_CORE_GTO5_ACCEPTANCE_2026-11-02.md)。

## 固定输入与范围

| 输入 | 身份 |
| --- | --- |
| LT 实测源码 | `1d1cf9d8ef8e87bf5b325de8ccdc5c548f325fac` |
| LT 正式包 | `ae2lt-forge-1.20.1-2.1.3-gto.1.jar` |
| LT 正式 SHA-256 | `4d3cec2c987f8511fb427b495f92bb65319ebad225f044502f621660b714e8ae` |
| Core 坐标 | `com.moakiee.thunderbolt:thunderbolt-reborn-forge-1.20.1:2.0.4-gto.5` |
| Core 原始包 | `thunderbolt-forge-1.20.1-2.0.4-gto.5.jar`，5,472,513 字节 |
| Core 原始 SHA-256 | `902c439f64c13ca776fb28c4c9c29aca70fe3083b0b65b5e744cad399f1bb2a1` |
| Core 交付源码 | `19e1e6fa3f9b93330f038b2d4d76f1d4fc3f9afb` |
| Core 本轮源码测试 HEAD | `1d6abc80c2f39a7c77c0d875a8e1489ce3c2d76e`；相比交付仅三份契约文档变化 |

环境为 Minecraft `1.20.1`、Forge `47.4.20`、JDK `21.0.10`。
Dev23 使用 GTOCore `26.10.10`／AE2 `15.2610.31`；Dev25 使用
GTOCore `26.10.18`／AE2 `15.2610.40`。原始 AE2 哈希、QA／instrumented 哈希和
原有 12 份消费源码的 LF 哈希均保留在机器基线中。

真实 Forge 与客户端使用原始 Core 发布包。夹具使用对应最终 R6 的 instrumented LT，
其全部 **2370 个生产类**与正式 LT 逐字节一致。Core 源码 JUnit、官方开发映射 ABI、
真实世界故障注入、真实 FX 和图形客户端分别统计。

## 本轮新执行

运行标签为 `joint1103r1`，使用新的世界、日志与 XML 目录；没有重记历史 `.3/.4` 或 R5 结果。

| 范围 | 实际结果 |
| --- | --- |
| Dev23 GameTest | **25/25**：原生批量 5、基础／共享种子 11、真实 NeoECO 2、规划／退款边界 7 |
| Dev25 GameTest | **44/44**：上述 25 项，加 exact／BigInteger／混合路由 19 项 |
| LT 三秒绝对期限／取消／正常预览 | **3/3 JUnit**；排队时间计入原有同一三秒期限，worker 不回访活动 Grid |
| 真实 FX | 两版各通过一次 |
| 独立保存恢复 | 两版各 prepare→resume→verify 三个独立 JVM，共六阶段通过 |
| 图形／TCP 多人 | 两版各数量界面、双客户端确认／取消、每人八次重连，共 **6/6** 场景 |
| 截图／网络证据 | **28** 张截图核 SHA、**12** 面板实际查看、**16** 条唯一 GUI ACK、**32** 次真实重连登录 |
| Core 定向源码回归 | **92 通过、1 失败、0 跳过**，共 93 项 |
| Core 审查 P1 两类 | **17/17**，为上述 93 项的子集，不重复相加 |
| Core 首失败原预算独立复跑 | 新 JVM **1/1** 通过，保留首次失败 |
| Core 官方开发映射 ABI | 原始 AE2 `.31`／`.40` 各 **14/14** |

Core P1 包含库存 A5/B14、请求 T23、可执行 T26 的见证，以及同一会话恢复额度耗尽和
共享工作额度耗尽后的 UNKNOWN／DECLINE 传播。正常缺料、可执行控制与截断保持区别。

共享种子、完整 AEKey、UUID、批量拒收和部分回存继续受既有数量断言约束。
UNKNOWN 退款故障接收器向真实世界箱子放入 2 后抛异常；保存、取消、同步快照、旧 NBT
及移除路径均验证不重放。跨 JVM 从世界 SavedData 恢复同一 owner/job UUID，保持
`physical=2 / spendable=0 / unresolved=10`。未结 10 是责任声明，不是额外库存；
原普通订单仍为同 UUID、输入 3／产物 3。

查看的图形面板包含完整数量 tooltip、样板编码页、LT 报告、原生算法名称、数量超限预览
及重连后的世界。Dev25 的长标题沿用原生滚动控件；数量超限状态可见。
本轮没有新增图形适配或放宽注入检查。

## Core 首次超时与独立全量回执

失败为 `PlanningAttemptMonitorTest.unmanagedInterruptStillReportsNativeCancellation()`：
第 43 行 `outcome.get(2, SECONDS)` 抛 `TimeoutException`，首次用例耗时 **2.105 秒**。
相同方法在独立新 JVM、相同两秒上限及全部原断言下复跑通过，耗时 **1.292 秒**。

只读分析确认，两秒观察窗包含首次线程调度、类／日志／参数初始化，没有 ready 栅栏；
普通返回与异常路径都会完成 Future。此用例直接调用 monitor checkpoint，没有调用
CP-SAT；内部 5／6／10 秒阈值与此次外层两秒观察超时分别处理。缺少失败 worker 的
当时线程栈，尚不能将原因确定为负载、冷态、日志锁或算法错误。未修改断言、预算或源码。

首次失败 XML SHA-256 为
`c10f595045792fe1eddb3508e355543d52959bc462b732127020b02f60cd3705`；
独立复跑 XML SHA-256 为
`d3e439a6a5bc23aa0a7a994afda5d1ff4b5b99992fc9251403a075b67970d4ae`。
最小后续诊断是对此方法采集类初始化／JFR 和超时边界线程栈，无需重跑整套矩阵。

Core 成员另行交付了独立全量通过回执：JUnit **1693 通过、2 项缺可选依赖跳过**，
能力参考 **82/82**、冷 JVM 回归 **23/23**、双 ABI 各 **14/14**、冻结原包双 Forge 各
**8/8**。来源为协作消息 `94454f1d-7ce4-46db-af22-1a6170c77ab2`，对应
[Core PR #17](https://github.com/AE2-Lightning-Tech-Reborn/Thunderbolt-Core-Reborn/pull/17)。
这些是 Core 成员的独立执行，不加入 LT 总数，也不抹去本联测首轮超时。

## 证据与交付边界

[机器基线](docs/baselines/lt-core-gto5-joint-retest-20261103.json)保留阶段回执、XML、截图、
接收者所有权及源码哈希摘要。原始联合索引 SHA-256 为
`6746d10be12633b625d9fed9c1020dead2d032d5b64d8415f23067ed2b520227`；
原始 Core 索引 SHA-256 为
`0b02a8651138e9c1ebeaa03a6ffe85a375b81c53f9c69f37f421f1f00d7c6ef1`。
原始日志、XML、图片和驱动保留在本地忽略目录，不加入提交。

公开 Core 接口与外部 NeoECO 桥签名无需变更，NeoECO 集成保留。
UNKNOWN 退款继续隔离，等待权威外部回执；保存验证使用有序退出，不声称跨模组断电原子性。
本轮未重跑性能基准，没有新的速度结论；正式合并、公开制品发行分别由发布流程处理。
