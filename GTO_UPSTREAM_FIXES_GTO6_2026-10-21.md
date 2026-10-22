# 上游修复移植至 Forge 1.20.1（gto.6）

本轮基于已有 GTO 工作树继续移植，版本为 LT `2.1.2-gto.6`，精确依赖原配套 Thunderbolt Core `[2.0.3-gto.5]`。维持 Minecraft 1.20.1、Forge 47.4.20、GTOCore 26.10.5 和 AE2-GTO 15.2610.8。Core 本轮无代码变化。NeoECO 清除继续保留。

## 来源与行为

| 上游提交 | 移植内容 |
| --- | --- |
| `e337a8306067e95faae373fe903c6e49e2c41205` | 大数终端广播回调只在服务端读取库存快照并同步，客户端提前返回。保留 Forge/GTO 的注入描述符。 |
| `c088c5df9177017e9102e42ffb47024cf55c3f6d` | 正常补料等待继续参与 100 tick 公平窗口；避免等待期间重复增加调度额度；单份派发也按配方统计，避免交错配方固定落到同一机器。 |
| `3c7df23fb37cd865cd321e4800bfb3b848188fe7` | 频率卡、菜单、记忆卡、无线连接器及延迟自动连接检查玩家对目标方块的权限；矩阵与天枢自动建造发送带玩家身份的放置事件，并在拒绝时回滚。 |

每位玩家担任 OWNER 的频率上限默认 16，可通过 `wirelessFrequency.maxOwnedFrequenciesPerPlayer` 调整。创建和晋升 OWNER 均受限制；下调配置不会删除既有成员关系。加密频率密码尝试按玩家跨频率共享一秒五次的滚动窗口，第六次断开当前连接；浏览和已有权限的使用不计入尝试。

网络协议保持 13。新增 OWNER_LIMIT 使用原有整数响应字段，没有改变数据包布局；两端一起更新才能显示新增的对应错误文本。

## Forge/GTO 适配

- 保护事件使用 `MinecraftForge.EVENT_BUS`、`Event.Result.DENY` 和 `ForgeEventFactory`；权限探测期间本模组事件处理器跳过实际操作，避免递归和副作用。
- 放置使用 Forge 方块快照捕获、单方块/多方块放置事件、反向强制恢复及放置成功后的通知；恢复标志始终还原。Forge 快照使用 `getReplacedBlock()`、`getFlag()`、`restore(true, true)`。
- 记忆卡保留 `CompoundTag`；网络保留 `FriendlyByteBuf`、Forge Context 和 GTO 原有手持物、距离及菜单令牌检查。
- 公平派发保留 Java 17 可用的 `Math.max/min`。新增测试使用 GTO 的 `GenericStack[]`，没有导入新版 AE2 的 List 返回类型。
- 未引入 NeoForge 构建配置、JEI 19、LDLib2 或无线 FE 实验功能。JEI 15 保持现有已适配路径。

## 验证

完整 `check`、强制重跑 `test`、`jarJar`、`gtoQaJar`、`gtoQaLtJar` 和全部 `compileJdbJava` 通过。单元测试 1548 项：1543 通过、5 跳过、0 失败。相关四组为终端两项、密码窗口三项、公平调度九项和供应器分配七项，全部通过。初次编译发现 Forge 快照取标志方法名差异；首次完整构建发现新增 AE2 测试替身返回类型差异，修正后完整重跑成功。失败日志保留。

正式包与冻结 QA instrumented 包的 4530 个非 manifest 生产条目逐字节一致。正式 LT/Core 无 NeoECO 字符串或入口，也没有新增权限测试夹具。静态审计检查 5297 处外部成员引用、513 个目标类，已安装依赖的缺失成员为零；37 处缺失属于未安装的可选附属。1884 个具体类无遗漏抽象方法，1427 个 JSON 全部解析通过；中英文各 1543 个键，键与占位符一致。历史同值重复键保留，没有新冲突。

隔离 GTO 专用服每组使用独立新世界，五组共 **53 项通过、0 失败**，各组退出码 0、无超时，CPU / STORAGE / PLANNER 三个启动探针均通过：

| 组 | 通过 |
| --- | ---: |
| 新增权限：OWNER 创建/晋升/降级/重载、设备入口、连接器、放置回滚、密码预算 | 5 |
| 基础合成与时轮状态 | 11 |
| 大数合成、报告、混合提供器与网络往返 | 19 |
| 通用批量与退款归属 | 5 |
| 正式 LT 内容与配方 | 13 |

原始结果为证据目录 `regression-results.json`，逐项日志位于 `runtime/server-*.log`。

首轮客户端因测试驱动漏准备新世界的 `level.dat`，停在数据包加载失败界面；该轮未进入世界，结果记为失败并保留在 `client-smoke-results.json`、`client-first-attempt.json` 和 `runtime/client-smoke.log`。随后从已有隔离测试存档复制出独立的 `GtoQA-upstream-fixes-gto6-20261021-r2`，保留首轮目录并重新执行客户端检查；没有修改生产代码来绕过失败。

第二轮图形客户端两项检查均通过、退出码 0、无超时：正式 LT / Core 检查 93 个启用 Mixin、73 个目标类，7 个可选目标跳过，数量格式和界面转换通过；instrumented LT / 正式 Core 实际打开原生与天枢终端，验证精确库存包、完整大数提示、long 缓存删除和精确数量恢复。已查看并归档世界渲染及两个终端截图。结果见 `client-smoke-results-r2.json`、`client-stock-results-r2.json`，日志与截图见 `runtime-client-r2`。终端测试不等于真实 WCWT 清除样板操作测试。

整包日志仍有其他模组资源警告，正式客户端退出期间还出现 EMI 后台搜索构建的 LWJGL 空指针；它不影响本轮探针结果，但不能把这些通过结果解释为整包日志没有错误或所有 EMI 搜索条目都已验证。本轮未重跑联机稳定性、全部机器、IO 和旧存档矩阵。全部最终结果汇总为 `final-results.json`，首轮测试环境失败单独保留。

新增权限游戏测试只进入可选 QA 包，通过 `-Dae2lt.gtoQaSuite=permissions` 运行。生产包不含这些测试夹具。当前隔离整包未安装 FTB Chunks，不能据此宣称完成真实 FTB 领地组合测试。

## 已知边界

历史 `nativeSubstitutionWaitsForAlternativeMaterialsWithoutPeriodicAttempts` 替代材料返回后未唤醒，以及联机偶发登录超时，见 [此前测试记录](GTO_NEOECO_REMOVAL_2026-10-21.md)。本轮公平补料修复不等于解决时轮替代材料唤醒问题，也不据本轮结果宣称全面无 bug。

已有未提交改动、历史制品和报告保留，没有提交或推送。移植前备份、上游补丁、构建日志和本轮独立证据目录为 `D:/codex-builds/ae2lt-gto-realtest-20261019/upstream-fixes-gto6-20261021`。

## 配套成品

正式游戏使用以下两个主 JAR，客户端和服务端一起更新。历史 gto.5 LT 成品保留；Core 文件继续使用原 gto.5。

| 制品 | SHA-256 |
| --- | --- |
| [ae2lt-forge-1.20.1-2.1.2-gto.6.jar](build/paired-release/ae2lt-forge-1.20.1-2.1.2-gto.6.jar) | `236e910fa47fde34bf268e4bc54d561a8b6b3876bd3f38d2970f2ecd08a29ad8` |
| [thunderbolt-reborn-forge-1.20.1-2.0.3-gto.5.jar](build/paired-release/thunderbolt-reborn-forge-1.20.1-2.0.3-gto.5.jar) | `37dd380498f38d9f904844340265aaf9a01c483240abb3bb3e44ffb1653e30de` |

校验清单为 `build/paired-release/SHA256-gto.6.txt`。Core 源码及完整可复现补丁继续使用 [gto.5 记录](GTO_NEOECO_REMOVAL_2026-10-21.md)，无需叠加其他 Core 补丁。
