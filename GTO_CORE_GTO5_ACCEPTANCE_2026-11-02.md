# Core 2.0.4-gto.5：LT 第⑧步增量验收

**A1–A9 增量验收完成。** 本轮固定同一 Core 正式包，补齐并修复 LT 的两个缺口：
外部退款无回执时先隔离转移责任，防止恢复、取消和移除后重放；Dev25 为已核验的
Core `.4/.5` 启用原有确认页适配，保留算法名和预览状态两处文本功能。
Core 公开接口与外部 NeoECO 桥签名均无需修改，NeoECO 集成完整保留。

## 固定输入与制品

```text
com.moakiee.thunderbolt:thunderbolt-reborn-forge-1.20.1:2.0.4-gto.5
5,472,513 bytes
SHA-256: 902c439f64c13ca776fb28c4c9c29aca70fe3083b0b65b5e744cad399f1bb2a1
Core source: 19e1e6fa3f9b93330f038b2d4d76f1d4fc3f9afb
LT development start: 093d71d950220b71463ab514d86b4a1f42014b2c
```

直接核验交接冻结包及 Maven Local 两份原始 `.5` 的完整大小和 SHA，再冻结本轮输入、
逐实例按 SHA 暂存；没有使用 CI 重建包替代。Minecraft `1.20.1`、Forge `47.4.20`、
Microsoft OpenJDK `21.0.10`，沿用 LT Gradle `8.8` wrapper。

| 环境 | GTOCore / AE2-GTO | AE2 原始 SHA-256 |
| --- | --- | --- |
| Dev23 | `26.10.10` / `15.2610.31` | `f63917bf5a2ca0692c0550ad2d045991f7a493d89021bfb30b51026d4ad31a3d` |
| Dev25 | `26.10.18` / `15.2610.40` | `b0d97544e618d73ec0c8d8e54df36351bf7f98f7e1984c6455d81658b6320b5c` |

可选组合实际包含 NeoECO `20.5.2-gto.1`、EAEP `1.6.3`、Useless `20.2.4.7-gto.1`、
真实 FX/FluxNetworks。固定 NeoECO 源码 `381e3f844beca250b0f824017f0fe585c9364144`
（源码版本 `20.5.5-gto.1`）只作参考；没有复制 GPL 实现或改动 Core 项目文件。

最终正式 LT `2.1.3-gto.1` SHA 为：
`4d3cec2c987f8511fb427b495f92bb65319ebad225f044502f621660b714e8ae`。
正式包与对应 instrumented 包的全部 **2370 个生产类**逐字节相同。QA 单独声明
`[2.0.4-gto.5]`；新增探针、断言和故障注入均不进入正式包。

图形复验 R5 的 LT SHA 为 `e663b9ffd8f0eab8c76cf052e92ccc31bd7ed9533ee7f9201a9876d5cb06ede5`。
其全部客户端/Mixin 字节与最终 R6 相同；R6 只增加服务端 `BigCraftingJob`、
`BigCraftingService` 退款隔离，内部 `MatrixTargets` 的行号元数据随源码移动，
其 `javap -p -c` 输出不变。退款、exact、真实 FX 与新保存生命周期在最终 R6 包独立复验。
早轮完整批量/种子基线使用 `00d57b81483167a51856501bc6a76507fdea6ac973c36d1ae19f0cc6dbe2c2ee`；
这些制品身份、阶段原始回执哈希和源码 LF 哈希均分别记录在
[机器基线](docs/baselines/lt-core-gto5-acceptance-20261102.json)，没有混记为同一个 LT 包。

## 修复及红绿证据

### 外部 UNKNOWN 退款

实际 Forge 中，带故障注入的外部 MEStorage 向真实箱子放入 2 份后抛异常，没有返回
已接收数量。旧 LT 仍把原额 10 保留为可退款 escrow；真实 Job NBT 保存/加载后，
下一 tick 再次退款，箱子累计 **12**。两版均正常记录三项汇总：零接收、部分回存通过，
UNKNOWN 失败；原始 `10→12` 红测及世界保留。

按第①步回执 `cd991e03-08f6-4ad9-9f07-5429ad970da2` 的结论，现有 Core 接口足够，
由 LT 负责持久化隔离。修复在进入外部代码前把 offer 从可用 escrow 移入
`unsettledRefunds`；有效回执才释放已确认未接收的余量，保留同步回调新增的独立库存。
异常或无效回执留下未结敞口并暂停订单，不自动重试；恢复、取消、移除与清理都受同一栅栏约束。

最终每版 **5/5** 外部回存用例通过：零回执、部分回存、UNKNOWN 保存/取消后不重放、
外部回调期间捕获的快照不重放、旧版本已知库存读取并退回。UNKNOWN 对照中，箱子为 **2**、
可用 escrow 为 **0**、唯一未结转移声明为 **10**。该声明只记录待核对责任，不计入
可用物料或库存总数；无权威外部回执时保持暂停，不猜测已接收数量。

私有 exact Job NBT 写入版本升为 **3**，保留读取 **1/2**；旧读取器会拒绝版本 3，
避免降级时静默丢失隔离状态。控制器原有 SavedData 所有权和 UUID 格式继续沿用。

### Dev25 客户端

首轮 Dev25 图形启动在 `thunderbolt$previewStatus` 的 `(0/1)` 注入检查失败，未进入界面。
实际 `.3/.4/.5` 的 `CraftConfirmScreenMixin.class` 均为
`ff380c44002d0c803dbfe07ee761a8d510e1b72114e66ad9726617de873c81e7`；
LT 原有适配版本白名单只列到 `.3`，导致 `.5` 没有注册该适配。

现补入已核验的 `.4/.5`，继续保留目标形状、两处回调、注入数量及文本内容检查。
真实图形/多人重新执行后两版全部通过，原失败回执和崩溃日志保留。

## 验证结果及 A1–A9 对应

| 检查 | 本轮实际结果与范围 |
| --- | --- |
| A1 字节、API、加载 | `coreConsumerAudit` **6/6**，`optionalApiAudit` **22/22**；两版真实 Forge/Mixin 与客户端加载通过。110 Core 消费类型、351 引用/207 成员、93 继承关系、两个反射构造器和七个注入/Shadow 锚点均保留。 |
| A2 UNKNOWN/预算/取消 | 每版 **2/2** 实际规划对照：66,000 输出的真实超限导出必须 DECLINE；完整缺料与可执行控制保持区别。LT 预览新增 JUnit **3/3**：队列等待计入同一原有 3 秒绝对期限、排队取消、正常成功且 worker 不回访活动 Grid。 |
| A3/A4 批量、共享种子 | 同一 Core `.5` 的早轮实际专服基线 **55/55**：Dev23 批量 5+基础/种子 11+真实 NeoECO 2；Dev25 同项再加 exact 19。覆盖完整键、共享种子、UUID、拒收、部分接收与 UNKNOWN 停止。 |
| A5 外部桥 | 最终包两版真实 NeoECO 主机/facade 各 **2/2**；协议/所有权测试覆盖借用输入快照、prepare UUID、缩量、默认回调、一次结算、普通回退及不确定状态传播。 |
| A6 退款归属 | 最终包两版各 **5/5**；物料放入真实世界箱子，显式注入回执故障；保存、取消、同步快照及旧 NBT 控制均通过。 |
| A7 Dev25 exact | 最终包 **19/19**；BigInteger、混合 provider、报告与路由在新私有 Job 格式下复验。 |
| A8 独立保存恢复 | 最终包每版 prepare→resume→verify **三个独立 JVM**。原订单同 UUID、输入 3/产物 3；另一个控制器所有的未知退款订单在世界 SavedData 中保持同 UUID、physical=2、unresolved=10、spendable=0，恢复/取消/移除均不重放。 |
| A9 图形/多人 | R5 **6/6**：两版精确数量界面、两版双客户端确认/取消、两版八次重连。真实 TCP 两玩家各阶段 ACK 每版 8 条；重连每版每玩家 8 次，共 32 次登录。28 张截图哈希核验，检查两版共 12 个主要界面面板。 |
| 最终受影响服务端复验 | R6 **37/37 GameTest**：Dev23 边界 7+真实 NeoECO 2；Dev25 同项再加 exact 19。两版真实 FX 和上述两组三 JVM 生命周期全部通过。与早轮 55 项有重叠，分别报告。 |
| `check` / `build` / QA | 主 JUnit **1932 通过、22 跳过、0 失败/错误**；正式/QA 构建、包校验通过，发布脚本测试 **12/12**。 |

22 个主任务跳过项中，21 个是另行显式运行的字节/API/身份门控检查，1 个是被 GTO
替代的旧 CPU 夹具；跳过不计通过。API/协议任务中部分协议断言与主任务重叠，不相加
冒充新增覆盖。静态解析、协议夹具、真实机器、独立 JVM 与图形 TCP 回执分别保留身份。

映射测试 Core SHA 为 `3386b0f93768609a3aeb2e59a699fe5ea9d0fa9225d43b54c8290cbc3876d88d`，
该 SHA 仅标识开发映射输入；实际整合运行始终使用原始 `902c439f…` 正式包。
包检查包含 CRC/唯一路径、1438 JSON、473 PNG、Java 21 class、Mixin/refmap、许可证和
嵌套依赖，确认没有 Core/NeoECO 实现或本轮 QA 混入正式 LT。

保存探针显式设置测试 Grid/故障回执及控制器所有权前置条件；生产控制器的移除、
`createRuntimeStateSnapshot` 和世界 `ControllerMachineStateSavedData` 负责真实持久化。
checkpoint 仅记录 owner/job UUID，后两个 JVM 从世界 SavedData 取回 Job。
这是有序保存退出验证，不宣称突然断电时存在跨模组原子事务。

## 交付边界

Core 公开接口和 NeoECO 桥签名保持不变；LT 配套为 `.5` 精确依赖、内部退款账本、
私有 NBT 版本及已支持客户端形状的版本门控。默认预算、测试数量、注入 require 与退款
断言没有放宽。正常已知退款继续自动完成，未知结果需要权威外部回执核对后才能解除隔离。

原始输入、XML、失败/通过回执、截图及本地驱动在忽略目录
`build/core-gto5-acceptance-20261102/`，不提交开发环境日志。首次异步 AssertionError
越过 GameTest 导致的专服崩溃/零 summary 也保留；随后只修夹具使用 `h.fail`，仍复现
相同失败，再以产品修复转绿，没有将零执行记成通过。

本轮更新既有草稿 [PR #54](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/pull/54)，
只向原 PR head 上传汇总，不推送本地开发分支、过程提交、标签，也不合并远端 `gto`。
正式发行、Core 制品公开获取及跨项目第⑨步汇总继续由相应发布流程处理。
历史 `.3/.4` 结果未移记为 `.5`。Core 第⑦步基准仍保留默认组 legacy-nine-distinct
`+51.6%`、独立 Xbatch 同例 `+4.8%`、shared `+1.3%` 的限制，本轮不声称稳定提速或无回退。
