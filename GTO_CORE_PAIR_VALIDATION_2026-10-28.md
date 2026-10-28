# 最新 Core 与 LT 配套验证（2026-10-28）

本轮获取 Thunderbolt Core GTO 最新可用构建，完成构建、专服、真实图形双人、可选集成、FX 和跨 JVM 恢复验证。复测发现并修复 Forge 握手待确认列表的跨线程竞态、Dev25 的两处 QA 键夹具不兼容，以及独立 QA 包漏带测试配方。最终 LT 为 **`2.1.2-gto.12`**，NeoECO 继续保留；最终验收组通过，LT 单测有 1 项明确跳过。

业务基线先使用 `.11` 完成，随后用 `.12` 重跑全部单测、两版图形联机、Dev25 基础／NeoECO、真实 FX 和强杀恢复。本文分别记录实际制品，不把 `.11` 的历史用例改标为 `.12` 执行。

## 实际制品与环境

Core 源码为 `3642511c3eb35af7ec7f3f12b744660eccdf7e25`，JAR 元数据确认为 `2.0.3-gto.6`。本轮查询远端 GTO 分支和 GitHub Releases，没有发现更高的可用 GTO 构建；普通 beta 发布不作为 GTO 制品。获取时最新本地构建与此前发布包的全部 ZIP 条目逐字节相同，归档 SHA256 因重打包不同。Core 源码未修改，实测 JAR 已独立封存。

| 制品 | 实际 SHA256 |
| --- | --- |
| 最终 LT `2.1.2-gto.12` | `4abce1ea5dd606ecec2c26337e5ecf723c332b2223056095e046f04cbae15f18` |
| 本轮封存 Core `2.0.3-gto.6` | `ecd76ddbf4899095991d52b5960e5e2183f28e1f5725d06e7d6b301cf3e920b6` |
| NeoECO `20.5.2-gto.1` 原件 | `c535ea3256a1ab5b0ad6025a28b5ead90e295b3fe7c2a1198246c1369a4d7f8b` |
| 前置业务基线 LT `2.1.2-gto.11` | `67b1827b5424390c45299be6454d65f7b65936513dfcf59e3cd13fee845f013c` |

Core 后续在本轮检查中再次重打包，归档哈希为 `d93c9a…`；实际游戏进程始终加载表中的 `ecd76d…` 封存副本。此前 Core 发布包哈希 `85ae8b28afacd951d19af79a1ad8708d01f29e834e17d7e0448772ed4bbddd1f` 也没有混作实际运行包。

Minecraft `1.20.1`、Forge `47.4.20`、Java `21.0.10`：

| 环境 | AE2-GTO | GTOCore | EMI |
| --- | --- | --- | --- |
| Dev23 | `15.2610.31` | `26.10.10` | `1.1.25` |
| Dev25 | `15.2610.40` | `26.10.18` | `1.1.1027` |

Dev25 客户端依赖与官方发布 manifest 核对。每次 JVM 启动均保存实际 LT、Core、QA、AE2、GTOCore、NeoECO 和可选模组哈希；最终汇总覆盖 69 份进程制品清单，包含原始失败现场。

## 构建和自动测试

Test 任务通过独立 init 脚本强制实际重跑并关闭构建缓存，XML 分轮封存，未把 UP-TO-DATE 或旧报告当作本轮执行。

| 检查 | 结果 |
| --- | --- |
| Core `check verifyReleaseJar compileGameTestJava` | 主套件 1,538／1,538 通过；另有 23 次独立冷启动检查通过，0 跳过。提供真实 EAEP 1.6.3 后执行了原先缺夹具的用例。 |
| LT `.11` 业务基线 `test build` | 1,608 项：1,607 通过、1 跳过、0 失败／错误。 |
| LT `.12` 最终 `test build gtoQaJar gtoQaLtJar -PgtoQa` | 1,611 项：**1,610 通过、1 跳过、0 失败／错误**，含新增的 3 项真实 Forge 方法并发回归。 |
| `adaptiveBatchStress` | 6／6 通过；业务实现与最终 `.12` 逐类相同。 |
| `.12` 正式包检查 | 通过：2,166 个生产类、1,427 个 JSON、依赖、Mixin／refmap、Java major、ZIP／嵌套归档、许可证与 QA 排除。 |

LT 唯一跳过项要求普通映射原生 CPU；当前 GTO 已替换该类。两版实际 GTO CPU、剩余产物桥、存储与规划诊断由专服组执行，跳过项未计入单测通过数。

`.11` 与 `.12` 制品比较确认，2,164 个生产类逐字节相同；变化仅为新增 `GtoHandshakePendingMessagesMixin`、对应启用条件，以及版本／Mixin 注册等资源。正式包与其 QA 副本的全部 2,166 个生产类也逐字节相同。

## 专服业务基线与最终包补测

`.11` 配合本轮 Core 共 **271 次 GameTest 执行**，另有真实 FX 场景。每组核对非零注册数、完整执行数、退出码和失败标记，常规组 CPU／STORAGE／PLANNER 三项诊断也通过。最终 QA 键夹具在两版 I/O 各 40 项中复测，旧候选通过记录没有重复加总。

| `.11` 最终业务组 | 数量／范围 | 结果 |
| --- | --- | --- |
| dev25-base | 11 | 通过 |
| dev25-neoeco-fresh-cache | 2 | 通过 |
| dev25-permissions | 5 | 通过 |
| dev25-exact | 19 | 通过 |
| dev25-dispatch | 20 | 通过 |
| dev25-interfaces | 6 | 通过 |
| dev25-key-contract-io | 40 | 通过 |
| dev25-infinite | 1 | 通过 |
| dev25-content-recipes | 13 | 通过 |
| dev25-batch | 5 | 通过 |
| dev25-fx | 真实 FX 场景 | 通过 |
| dev25-without-neoeco | 11 | 通过 |
| dev23-base | 11 | 通过 |
| dev23-neoeco | 2 | 通过 |
| dev23-exact | 19 | 通过 |
| dev23-key-contract-io | 40 | 通过 |
| dev23-key-fix-infinite | 1 | 通过 |
| dev23-eaep-1.5.5 | 9 | 通过 |
| dev23-eaep-1.6.3 | 9 | 通过 |
| dev23-useless | 8 | 通过 |
| dev23-if-mekanism | 20 | 通过 |
| dev23-more-machines | 2 | 通过 |
| dev25-eaep-1.6.3 | 9 | 通过 |
| dev25-useless | 8 | 通过 |

可选模组使用真实发行包：EAEP 1.5.5／1.6.3、IF／Mekanism、MekanismExtras／MoreMachine、Useless 与 NeoECO 的指定路径分别执行。Useless 使用已校验上游哈希、仅调整元数据且保留所有类／资源／署名的独立 GTO 副本。没有把类加载计作机器功能通过。

| 可选模组实际文件 | SHA256 |
| --- | --- |
| `Mekanism-1.20.1-10.4.16.80.jar` | `3b0d191fa4a45e662f9725991d76a30192f30186abb2ba694b873b5f7b9358d2` |
| `extendedae_plus-1.5.5.jar` | `1d008e2fcd36c381e765ab2a09f8008dfec370604b2f72a15ace93c41b70ee02` |
| `extendedae_plus-1.6.3.jar` | `d8634db4ca56fe725799084b3c2c67b17874db50377ea497670379221d374764` |
| `industrial-foregoing-1.20.1-3.5.22.jar` | `9220ddde2c259c8dfa5c05ad23b26650dc9b2f3048c4aca3b1d9d6f4cd211f3d` |
| `mekanism_extras-1.20.1-1.5.0.jar` | `73dcdc8af6938ff711e041e1edf40315ac690b51daf93f96d3e697360a320596` |
| `mekmm-1.20.1-1.2.1.jar` | `cb2be6fd45a246778bed2de3232bf70c9edc37f784254f63e9e4d208cb7b4c95` |
| `useless_mod-20.2.4.7-gto.1.jar` | `1e47a95eafc03c7c40edc65dd949e226b5b31e4c58e3dc08ccfedbc3c01e5c6c` |

最终 `.12` 另在 Dev25 执行基础 11 项、NeoECO 2 项，合计 **13 次 GameTest**；全部通过，并核对其对应 `.12` QA 制品哈希。`.12` 正式包还重新执行真实 FX 和下述强杀恢复。

`.11` FX 使用生产类一致的 instrumented LT；`.12` FX 使用正式主包和补齐配方的独立 QA 包。两轮均构建 8 台完整 F9、88 个物理 FX、虚拟倍率 64。真实网络完成 `1267650600228229401496703205413`（`2^100+37`）次操作，库存产物 `5070602400912917605986812821652`，原料剩余为 0，未出现重复入库，物理线程清空。

## 最终 `.12` 图形、双人和恢复

Dev23、Dev25 均使用正式 LT JAR、独立专服和两个不同离线身份的图形 JVM，经正常主菜单／ConnectScreen 与回环 TCP 登录。

- 两版双人菜单通过：实际高级界面点击、两端关闭／开启同步、每组八次独立 ACK、新容器重开，以及 JEMI 运行时置空／恢复后的悬停查询。
- 原生和 LT 确认页通过，复核新截图：Core 算法标题、精确字节数、模拟预览和普通摘要恢复；Dev25 跟随按钮在卡片内、CPU 选择器上方，Dev23 保持其布局。客户端摘要夹具用于渲染，不代表提交这些摘要订单。
- 两版各两个图形 JVM、每人 8 轮登录／世界渲染，共 **32 次登录，其中 28 次是断线后重连**；服务端逐身份核对次数。最终验收关闭网络诊断并使用默认读取超时；专服和客户端正常退出。
- 两版运行时导出的真实 `HandshakeHandler` 构造器均调用新补丁，并把待确认序号字段改为 `CopyOnWriteArrayList`；没有更改原回执方法或协议字段。
- `.12` 在保存确认后直接强杀准备 JVM，退出码非零且没有正常停服；第二 JVM 恢复同一任务 `02455801-3704-4134-b6e2-beec610204ca`，消耗 3、交付 3；第三 JVM 确认任务不复活、磁盘产物 3、原料 0、处理箱为空。

## 失败追溯和修复依据

**Dev23 间歇断线。** `.11` 初轮客户端分别只完成 8／5 轮；服务端记录 `ConcurrentModificationException`，客户端随后报登录断开包剩余 93 字节。原现场没有完整服务端异常栈。仅异常日志及 JFR 两次诊断轮均完成 8／8，JFR 捕获 5,696 个异常事件但没有 CME；这些重试没有被当成修复证据。

检查实际 Forge 47.4.20 字节码发现，服务端 `tickServer` 向普通 `ArrayList sentMessages` 添加序号，Netty 的 `handleIndexedMessage` 同时执行 `removeIf`。新增回归直接提取真实 Forge 回执方法和 lambda，用受控重叠稳定复现 CME；执行实际补丁回调后，同一交错成功，未确认的新序号保留，重复／乱序回执也不会提前清空列表。[同类上游问题](https://github.com/void-community/ModLoaderFixes/issues/8) 提供了额外佐证。

修复在 GTO 环境的构造器末尾保留已有序号并换成线程安全列表。`.12` 随后执行上述两版真实客户端验收。既有 LOGIN 刷新、协议切换后始终排队恢复读取、主动暂停语义与 PLAY 合并均保留；未提高超时或吞掉异常。原始断线日志及修复前通过重试全部保留。

**Dev25 QA 配方缓存。** 初次 NeoECO 两项在查找 `ae2lt_qa:oak_planks` 时失败，尚未进入业务断言。QA JAR 内配方存在，但沿用正式包生成的 GTO 缓存没有 QA 命名空间。停服保存旧缓存后重建，同组两项通过；制品／可选组合变动时，隔离实例会备份并重命名缓存，原件未删除。

**正式包 FX 的独立 QA 配方。** 最终 `.12` 首次 FX 在第 2 阶段取 `ae2lt_qa:oak_planks` 时失败，尚未下单。该轮使用正式 LT，而原独立 QA 包也未收录配方；此前 instrumented LT 自带这些资源。调整 `gtoQaJar` 打包规则，让独立 QA 自带 3 份测试配方，停服备份缓存后在新世界重跑原 FX 场景并通过。新 QA 的 SHA256 为 `3b3188e65374c4c1136ac5129e862e0092d95456ac35e0ce129a54fdda19f80b`，原有 ZIP 条目全部逐字节相同；正式 LT 哈希保持不变。原失败结果保留在 `handshake-fix-dev25-fx.json`，复测记录为 `handshake-fix-dev25-fx-fixture-retry.json`，未减少机器、数量或断言。

**Dev25 QA 键契约。** 初次 I/O 组因 `CountingKey.hashCode()` 覆写 AE2 `.40` 的 final 方法而崩溃。修复两处 QA 夹具：Dev23 保留结构哈希和值相等键的原断言；Dev25 按规范身份工作，旧覆写类仅在非 final 环境反射加载。可序列化键在创建、NBT 和包解码时使用同一规范实例工厂。正／负缓存、1,000 次热命中、冷查询、规则反转、模糊规则及无限盘综合检查均保留。

`.11` 最终键夹具附件 SHA256 为 `0dd1bf4c7c9d82c2be2e4a1b049540d3b65731e2ce727fd24a46650886fe75e5`，其生产类与 `.11` 正式包逐字节相同；`.12` QA 附件另行封存并记录版本。没有通过删断言、减少数量、扩大预算或 skip 制造通过。

另一次 Java 代理诊断在专服启动时以 Windows `0xc0000374` 原生错误退出，未进入登录用例，原因未定位到具体原生库。该失败单独保留；最终验收未使用该代理。正常游戏日志仍有整包已有的 `fml:handshake` 空 payload、LDLib 专服扫描客户端注解等非致命消息，本轮没有静默它们。

## ECO 参考、证据与边界

按用户建议读取 [NeoECO GTO 源码](https://github.com/Yang120231/NeoECOAEExtension/tree/1.20.1-GTO)，核对 `ECOCraftingWorkerRecovery` 的任务 UUID／已加载工作线程恢复边界，以及 `CraftingCapabilitySnapshot` 的物理 FX／虚拟容量语义。参考树为 `2f77a9398969fa36994d90dab252ba8c9098413b`，读取的两个 blob 哈希与源码副本保存在本轮证据中。握手修复依据实际 Forge 方法，ECO 原项目和 AGENTS.md 均未修改。

证据目录为本机 `ae2lt-gto-core-latest-20261028`，包含 `final-validation-summary.json`、分轮 XML、基线与最终专服矩阵、四组最终图形 `*-pair.json`、强杀恢复结果、制品比较、运行时导出类、失败日志／崩溃记录、旧缓存及新截图。开发环境日志和 QA JAR 未加入源码提交；`release/` 仅含两份正式 JAR 与 SHA256SUMS。

联机范围是本地离线身份、回环 TCP 和有界重连；未验证公网正版认证及长期负载。强杀恢复发生在 `save-all flush` 确认之后，未覆盖写盘中途断电。恢复场景覆盖真实实体 CPU 的 LT 超载任务，处理箱代表外部处理器，在恢复后消费输入并通过实际网络回料；未覆盖 NeoECO FX 工作线程本身运行中的跨 JVM 恢复。可选模组结果限表中实际版本与执行路径，不代表所有附属功能。
