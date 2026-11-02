# GTL 规划入口与共享材料预检优化（2026-11-02）

基于本地 `1.20.1_GTL` 的 `f21f9139`，参考 [NeoECOAE 20.5.1 / d1e306c](https://github.com/DancingSnow0517/NeoECOAEExtension/tree/d1e306c) 中明确规划归属、共享库存校验和区分理论结果与执行能力的设计。本次实现位于 AE2LT 的规划策略与时间轮调度层，使用项目已有的 Thunderbolt Core GTL 2.0.4；没有复制 NeoECO 源码或新增依赖，既有 NeoECO 基线及上游排除清单保持不变。

## 行为变化

### 普通、大数量请求遵守相同的独占算法选择

此前普通 AE2 请求会在提交计算前重新应用天枢／GTL 超限控制器的独占选择，但 `ICraftingPlanningService.resolve()` 先应用了“无天枢 CPU 就删除 V2 / CP-SAT”的默认策略。只连接 GTL 超限 CPU 时，规划服务返回的候选因此可能与普通入口不同；独占原生选择也可能被较早的候选覆盖。

`TianshuPlanningServiceMixin` 现在先读取同一个 `ExclusiveCraftingPlanning` 决定。存在有效独占锁时直接返回其候选；没有锁时继续应用现有天枢可用性策略。已确定规划归属的请求也省去了枚举可用 CPU 的步骤。优先级、未知算法归一化和普通入口的禁止原生回退机制沿用既有实现。

### 共享替代槽位不再重复计算同一份库存

例如两个槽位各需要 2 个“铁或金”，库存只有铁 1、金 1。此前每个槽位独立看到 2 个候选，预检返回材料齐备，实际分配才失败；现在联合预检能证明总量不足，让时间轮任务等待相关材料变化。

新增 `SharedInputAvailability`，把物料与输入槽构成一个小型容量网络。每种材料采用所有相关槽位中最小的单位用量，得到偏乐观的容量上界；若在此上界下仍无法满足所有槽位，才报告缺料。这也能发现“两个槽位争抢少量铁／金，第三个槽位存在大量钻石”的局部共享缺口，避免无关富余掩盖缺料。

该检查只读取库存，不调用动态 `isValid`、返还容器计算或提取接口；通过预检后，真实匹配、扣料、种子账本和派发仍由原有分配器处理。补料和新 NBT 变体继续通过现有库存通知使缓存失效。

容量网络分别检查每条边和每个槽位的剩余需求，不累加可能超过 `Long.MAX_VALUE` 的总需求／总流量。联合检查最多处理 32 个输入槽、128 个材料键；过大的自定义样板，以及无法用饱和 long 投影证明总量的变体材料族，交给原有分配路径。该预检不把“无法证明缺料”当作可执行计划认证。

## 改动文件

- 实现：`CraftingMaterialReadiness.java`、新增 `SharedInputAvailability.java`、`TianshuPlanningServiceMixin.java`。
- 单测：`CraftingMaterialReadinessTest.java`、`TianshuPlanningBoundaryTest.java`；后者补齐独立运行所需的 Minecraft bootstrap。
- 开发夹具：新增 `PlanningOptimizationGameTests.java`、`PlanningOptimizationQaMod.java`。正式 Forge 不提供 GameTest 启动器且关闭自动注册，隔离 QA 包负责注册本次测试命令和逐 tick 推进，生产发行包不包含这两个类。
- 本报告。原有未提交的 `PatternMigrationGameTests.java` 修改经 SHA256 核对保持不变，不纳入本次提交。

## 验证

| 检查 | 本轮结果与范围 |
| --- | --- |
| 修复前行为对照 | 15 项中 6 项失败，复现两项规划路由、共享缺料、局部缺口、数量边界和穷举对照问题；原始 XML 保留 |
| 修复后定向 JUnit | 313 项通过、0 失败、0 跳过，包含循环种子、实际输入分配和材料预检 |
| 独立穷举 oracle | 固定种子 1000 组，2～4 槽、3 种材料；单位用量为 1 时与暴力分配结果一致，混合单位时不会拦截任何 oracle 判定可行的分配；库存保持不变 |
| 完整 JUnit | 1957 项通过、0 失败、0 跳过；启用既有真实可选 API 检查，未将接口检查等同实体机器验收 |
| 批处理压力模型 | 6/6 通过，与完整 JUnit 分别统计 |
| check / build | PASS，Java 17 / Forge 47.1.47 / AE2 15.4.10，Core 使用冻结 GTL 映射副本 |
| 发行包审计 | PASS，3 个归档、2590 个 Java 17 可用类、1528 个 JSON，Mixin/refmap、嵌套依赖、许可证与署名、既有 NeoECO 排除范围及开发夹具不泄漏检查通过 |
| 正式 GTL 专服 GameTest | 5/5 required 通过，结构化结果逐项为 PASS；正常保存退出，进程退出码 0 |

正式运行采用 Java 17.0.20、Forge 47.4.16、AE2 15.4.10、GTLCore 1.2.3.2-fix1、GTCEu 1.4.4。与既有验证整包相比，89 个 JAR 中 87 个保持相同 SHA256，仅替换 AE2LT 和本次隔离 QA 包（移除旧验收探针）。默认 Radium 配置在运行前后均保持相同 SHA256。测试使用隔离目录及本轮创建的测试世界副本，不改启动器实例或原存档。专服入口检查使用锁源夹具，不代替真实天枢／超限多方块机器验收。

首次独立单测的 bootstrap 初始化失败，以及首次正式 Forge 缺少 `forgegametestserver` LaunchHandler 的启动失败，均保留原记录；二者发生在对应业务断言执行前，不计为通过。

第一次普通专服运行注册并启动了 5 项测试，但默认 `LogTestReporter` 不记录成功回调，外层观察器等待到限后正常停服，退出码 0；该轮保留为未确认结果。最终在 QA 包内接入真实 `GlobalTestReporter` 回调，记录全部五个测试名称及结果，再在相同 360 秒进程上限和 20 tick 单项上限下复测通过；生产 JAR 字节未变。

## 制品与验收边界

完整包：`build/eco-inspired-gtl-20261102/release-final/ae2lt-forge-1.20.1-gtl-2.1.3-beta1.jar`，同目录附 SHA256。

SHA256：`94c0fcad3e075bb060d9c14ee0bdb991f8f981b5aea91e298faa25d870fe0c5f`。

配套 Core 冻结 SHA256：`efb0d63bc6fb2ab81f0778ab9f6792ec853468ddb61a1e90f6b0693b1639e06f`。过程证据、修复前后 XML、构建日志、正式运行清单及发行审计均位于 `build/eco-inspired-gtl-20261102/`，不加入提交。

本次代码、回归夹具与报告整理为一个本地汇总提交，未推送。

本次完成规划归属一致性与缺料预检优化，未测量整包 TPS/MSPT 的提速比例。完整整合包仍按原验收报告保留导出预算超限（2/1200 个 >50 ms tick）、有限供能、真实批量机器及客户端等未验收项。
