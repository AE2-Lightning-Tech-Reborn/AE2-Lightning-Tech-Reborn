# NeoECO 兼容清除与验证（2026-10-21）

按用户要求，当前 LT 与配套 Core 清除 NeoECO 兼容。版本更新为 LT `2.1.2-gto.5` / Core `2.0.3-gto.5`；LT 精确要求 Core `[2.0.3-gto.5]`，客户端与服务端应同时替换配套 JAR。网络协议保持 13，本次没有修改网络字段布局。

## 清除范围

- LT：删除 NeoECO CPU Mixin、Mixin 条目及条件加载注册；删除大数原生批量桥和专用取消/提交入口。普通提供器通过 `ICraftingProvider.pushPattern` 派发；原生批量、通用批量结算与延迟产物回流保留。
- 样板迁移：删除 NeoECO 物理库存适配、执行状态探测和共享迁移锁。LT 自有网络迁移锁、普通 AE2、ExtendedAE 与 EAEP 适配保留。
- 用户说明：同步清除中英文终端指南及迁移提示中的 ECO / F 系列支持描述。
- 配置与上传：删除默认限制表中的 NeoECO 样板总线和天枢自动上传白名单中的三个 NeoECO 合成系统 ID。不重写已有玩家配置。
- 配方：删除 5 条 NeoECO 晶体催化配方（包括 2 条粉尘配方）及 3 条 NeoECO 过载处理配方。
- Core：删除全部 `compat/neoeco` 实现、可选注册、协议测试和构建测试依赖；删除 NeoECO 测试参数。EAEP、AppliedE 与其他原生批量接口保留。
- 离线附属迁移工具：删除 NeoECO 专用字节码改写、发行文件哈希白名单及用例。其他附属迁移支持继续保留。
- 测试：删除 NeoECO 专用脚本、游戏测试和条件跳过入口；通用批量结算测试保留，可选适配注册测试独立检查 EAEP。通用规划/通配符回归中测试用命名空间改为 `fixture`，算法覆盖保留。

清除前工作树和暂存区分别备份到证据目录，既有未提交修改未重置。历史移植报告、补丁、日志与旧 JAR 保留为历史证据，当前兼容指南已更新。没有提交或推送。

## 正式成品

使用本项目 `build/paired-release` 中以下两个正式 JAR；QA instrumented 包仅供隔离测试。

| 文件 | SHA-256 |
| --- | --- |
| `ae2lt-forge-1.20.1-2.1.2-gto.5.jar` | `a498ff2d58b34f95fb9d6876192ff4b6d33c433217ca015b4ca90bb690f72c80` |
| `thunderbolt-reborn-forge-1.20.1-2.0.3-gto.5.jar` | `37dd380498f38d9f904844340265aaf9a01c483240abb3bb3e44ffb1653e30de` |

哈希文件：`build/paired-release/SHA256-gto.5.txt`。实机使用清除代码后的冻结 LT 包（SHA-256 `1cd007c5cc91c556fe9e36b4d0e5727b2725597245dd1a35293cb084e3430642`）与上述 Core。最终额外删除中英文指南和迁移提示中的 ECO / F 系列支持说明，重打包后仅 4 个文字资源和 manifest 时间改变；所有类文件及其余资源与实测冻结包一致。最终成品保存于证据 `artifacts-final`，原实测包在 `artifacts` 保留。文字资源变更通过独立差异、JSON 和残留核验，未重新运行全部实机测试。

## 构建与单元测试

| 检查 | 总数 | 通过 | 跳过 | 失败 |
| --- | ---: | ---: | ---: | ---: |
| LT 完整 check，强制重跑 test | 1,534 | 1,529 | 5 | 0 |
| Core 完整 check，含三个冷启动任务 | 1,531 | 1,530 | 1 | 0 |
| Core 迁移工具修改后定向复测 | 4 | 4 | 0 | 0 |

Core `jarJar`、`verifyReleaseJar` 和本地 Maven 发布完成。LT `jarJar`、`gtoQaJar`、`gtoQaLtJar` 完成。额外编译全部调试源码发现旧 `CraftingReportClientProbe` 的四处普通版 AE2 `Map` 参数与 GTO `Object2LongMap` 构造接口失配，已修正测试夹具；第二轮 `check compileJdbJava gtoQaJar gtoQaLtJar` 成功。生产代码不受此夹具修正影响。

初始两次 Core 命令引用了不存在的本地 Maven 任务名，尚未执行测试即退出；修正为实际 `publishMavenJavaPublicationToMavenRepository` 后完整构建通过，原日志保留。本次没有把启动错误当作测试失败或隐去记录。

## 清除完整性与静态审计

- 两工作区源码、测试、脚本和构建配置没有 NeoECO 引用；同时检查了旧类名、迁移锁名及仅记录为哈希的白名单项。
- LT / Core 正式 ZIP 全部条目的路径和字节内容没有 NeoECO 包、ID 或旧兼容类引用。迁移工具也没有 NeoECO 类名或发行哈希残留。
- QA instrumented JAR 与冻结 LT 正式包的 4,527 个非 manifest 条目逐项一致。
- 5,297 处外部成员引用、513 个目标类：37 处缺失全部属于未安装 AdvancedAE / AppFlux / AddonLib；已安装依赖缺失成员为零。
- 1,881 个具体 LT 类没有遗漏抽象方法；1,427 个 JSON 解析正常；中英文各 1,541 个键、占位符一致。
- 同值重复语言键 `jei.ae2lt.crystal_catalyzer.matrix_note` 为历史项，没有新冲突。

历史文档与旧补丁包含 NeoECO 旧记录，它们不是当前加载入口。已有玩家配置中的旧字符串不触发本项目专用适配；本次未安装或验证 NeoECO 与此构建的组合。

## gto.5 实机回归

隔离完整 GTO 专用服，每组使用独立新世界。五组共 67 项，66 通过、1 失败，各组 CPU / STORAGE / PLANNER 启动探针均通过；各组退出码 0，没有超时。

| 组 | 执行 | 通过 | 失败 |
| --- | ---: | ---: | ---: |
| base | 11 | 11 | 0 |
| exact | 19 | 19 | 0 |
| dispatch | 19 | 18 | 1 |
| batch | 5 | 5 | 0 |
| content-recipes（正式 LT） | 13 | 13 | 0 |

原 NeoECO 专用测试与跳过项已移除；批量组新增独立 EAEP 可选注册验证，因此为 5 项。

图形客户端使用正式 LT / Core 在独立 QA 世界启动通过，退出码 0；检查 93 个启用 Mixin、73 个目标，7 个可选目标跳过。配方转移断言及终端数量格式检查通过。客户端结果见 `client-smoke-results.json`、`runtime/client-smoke.log` 和 `runtime/smoke-mixin-result.txt`。

正式配套包在两个独立新世界顺序联机：第一轮登录阶段 `disconnect.timeout`，未入服；第二轮实际入服并通过客户端格式/渲染探针。两轮服务器与客户端均正常退出。结果为 1 通过、1 失败，不能认定联机稳定通过；结果记录于 `multiplayer-results.json`。一次误与服务端回归重叠启动的联机监督过程已停止，该过程不计入结果；证据见 `multiplayer-aborted.json`。仅停止了本任务确认过的进程。

## 已知未解决问题与测试边界

清除前全面测试发现的 `nativeSubstitutionWaitsForAlternativeMaterialsWithoutPeriodicAttempts` 在 gto.5 仍失败：返回白桦/橡木板各 1 个后，库存为 `1/1`，`dispatched=0; queries=1; busy=true`，任务没有按预期唤醒。这是清除前已存在的问题，本次未修改相关调度算法；不能宣称全面无 bug。清除前首轮失败、全组补测通过和隔离补测再次失败均见 [全面测试记录](GTO_COMPREHENSIVE_TEST_2026-10-21.md)。

本轮针对 NeoECO 清除执行完整单元与静态检查、主要合成/批量/配方实机回归及客户端检查；没有重跑清除前全部 IO、无限盘、机器和各客户端专项矩阵，也没有穷举整合包所有配方或长期运行。

## 配套 Core 源码与可复现补丁

独立源码：`D:/codex-builds/thunderbolt-infinite-port-20261020`，分支 `port/infinite-gto`。不修改其他同级工作区。构建主模组时传入 `-Pthunderbolt_local_repository=D:/codex-builds/thunderbolt-infinite-port-20261020/repo`。

完整公共基线：`7c7a066af9d404010fe0b9472fca687879b36281`。新的累积补丁为 [core-gto.5-from-upstream.patch](docs/patches/core-gto.5-from-upstream.patch)，79 个文件；SHA-256 `3d096e70f5d97073680929abac2ca47e791611297b85fd2aedbf3675a7345c5e`。使用独立 Git index 导出，不改变实际暂存区；基线正向应用及当前工作树反向检查通过。

所有原始证据在 `D:/codex-builds/ae2lt-gto-realtest-20261019/neoeco-removal-20261021`：源码清除前备份、完整单测 XML、冻结成品及哈希、静态审计、服务端/客户端日志、清除差异清单和补丁验证结果。
