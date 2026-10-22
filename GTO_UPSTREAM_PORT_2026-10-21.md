# 上游增量移植（2026-10-21）

配套版本：LT `2.1.2-gto.4` / Thunderbolt Core `2.0.3-gto.4`。继续使用 Minecraft 1.20.1、Java 21、Forge 47.4.20、GTOCore 26.10.5 及其内嵌 AE2-GTO 15.2610.8。客户端和服务端须同时替换两个主 JAR；LT 网络协议从 12 升至 13，拒绝与旧客户端混连。

## 来源与变化

| 来源 | 本轮移植 |
| --- | --- |
| LT alpha `fea5eead` + Core Forge `07f7883`（对应 alpha `afefa96`） | 合成确认报告不再恒为 0 字节。规划结果保留诊断计划，按完整需求、执行次数和返回物料计算精确字节成本；分数最终向上取整。原生 long 显示饱和，精确报告保留 BigInteger；无限 CPU 自身的字节预留仍为 0。可执行报告明确 `previewOnly=false`。 |
| Core Forge `cea4cc2` | 通过 `ECOExactBatchProvider.eco$prepareExactBatch` 探测 Forge 精确批次，读取 `craftCount` 和延迟执行回调；通过 `ECOCraftingJobLifecycle.cancelAndRecover` 取消并回收。保留旧 Forge long 协议、批量回执、UUID 普通回退以及异常后不重试的归属保护。 |
| LT alpha `39c575b7`（含 Mekanism 保护前置） | 超载供应器高级设置增加默认开启的“机器适配模式”。AUTO 周期回收与投料前回收保护 Mekanism 输入/辅助/能量槽，识别工业先锋已核对的产物槽/罐；不改变正常投料、EJECT 或第三方适配器旧接口。支持 NBT、旧存档缺字段、记忆卡、方块更新流和服务端菜单同步。 |

机器适配的包装缓存随开关刷新，两个供应器访问同一机器而设置不同时不会串用。原生 MEStorage 继续作为权威接口，未知机器保留通用回收；无法核对的已识别机器能力包装器采用保守视图。关闭适配后恢复通用 AUTO 回收。

本轮没有移植 main 中为了旧 Core beta2 而禁用精确 ECO 路线或替换本地 `BigLongSlice` 的回退。旧版 JEI 无线供货的调用描述符与本地 JEI 15 已有实现一致，未导入 JEI 19 的版本分流。上游 alpha `15ff586c` 已撤回 LDLib2 预览，本地此前保留的 GTO 原预览继续使用。

## Forge 适配

- Titanium 使用可选 compileOnly 依赖 `maven.modrinth:titanium:LMqbm4db`（1.20.1-3.8.35），不打入游戏 JAR；只有工业先锋和 Titanium 同时安装才进入其适配类。单元测试另外引用 Titanium 与现有 Mekanism 10.4.16.80。
- NeoForge 世界能力查询改为 Forge 的真实方块实体 `getCapability`。工业先锋流体路径仅允许已审计的 `ActiveTile.getCapability` 或 `PoweredTile.getCapability` 原生入口（后者仅截获能量，流体原样委托给 ActiveTile）、当前侧面真实返回的 handler 与同侧 `MultiTankComponent` handler 身份一致，并逐次检查 FaceMode。没有改用无面能力、强制填充或强制抽取。
- Forge FluidStack 通过 copy/setAmount 保留 NBT；记忆卡使用 CompoundTag，方块同步使用 FriendlyByteBuf；保留 GTO 的菜单及生产 API。
- 新增测试夹具仅进入可选 QA 包。原有 IO、无限存储、调度、自由视角等移植保留。

## 验证

LT `check`：1534 项，1529 通过、5 跳过、0 失败。Core 最终 `check`：1556 项，1543 通过、13 跳过、0 失败；其中主测试 1533 项，另有 23 项冷启动/回放回归。没有放宽规划器时间阈值，native 临时目录仍在 D 盘；历史时间敏感失败不据此宣称永久解决。

新增 NeoECO Forge 合约测试在隔离类加载器中编译协议替身，验证 101 位请求/接受数量、UUID 和借用输入、延迟提交、拒收/异常不触发普通重试、非法接受数量拒绝及旧版缺 exact API 回退。它们是公开接口合约验证，不是真实 NeoECO 机器测试。

LT 初次编译和夹具编译发现的 Forge API 差异均已修正。首次完整单测仅因旧源码合约仍要求协议 12 而失败；与本轮协议升级一起更新为 13 后，完整 `check` 通过。早期日志保留。

首次整包机器回归实际执行 20 项，其中 12 通过、8 失败：工业先锋流体路径只接受 `ActiveTile` 声明的能力入口，未识别 Forge Titanium `PoweredTile` 的能量专用覆盖，导致产物流体被保守拒收。已对照 Titanium 3.8.35 的真实字节码确认其流体原样委托，增加该原生入口白名单；同侧 handler 身份、类型及 FaceMode 检查继续保留。失败日志和汇总保存在 `runtime-r1/server-machine.log`、`server-results-r1.json`；修复后重新执行完整 `check` 和重混淆构建，最终运行结果见下表。

修复后的 `runtime-r2` 新世界完整回归：

| 环境与分组 | 实际通过 | 进程退出 |
| --- | ---: | --- |
| 未安装 Mekanism/工业先锋：基础回归 | 11 | 0 |
| 未安装 Mekanism/工业先锋：大数合成、报告与网络往返 | 19 | 0 |
| Mekanism 10.4.16.80 + 工业先锋 3.5.22：机器回归 | 20 | 0 |
| 仅 Mekanism、无工业先锋：可选依赖缺失回归 | 4 | 0 |

合计 **54 次用例执行通过，0 失败**；其中最后 4 次为不同可选模组环境下重跑 Mekanism 用例。每组 CPU、STORAGE、PLANNER 探针全部通过，未发生启动失败或超时。机器测试使用实际模组方块实体及其 Forge 侧面能力，覆盖相邻/无线、过滤/不过滤回收、辅助输入保护、动态侧面配置、拒收/补偿、共用目标开关、满网络/满回料缓存及正常投料/EJECT 回归。

客户端由正常主菜单进入隔离测试世界，EMI 检查通过并正常退出：原生 GTO EMI 注册、配方界面、渲染部件边界、旋转、缩放和外壳开关。截图已归档并查看为 `runtime-r2/emi-multiblock.png`。

正式 LT 与重混淆 QA 测试包的 **4214 个生产文件逐字节一致**（仅排除清单）；正式包无 QA、新测试夹具、JUnit、Titanium 或 Mekanism 依赖类，1435 个 JSON 全部解析通过，Core 精确版本依赖和协议 13 已核对，旧有五个重点测试夹具与修改前哈希一致。本轮临时安装的 Mekanism/工业先锋测试 JAR 已清理。最终汇总为证据目录的 `final-results.json`，制品核对为 `artifact-verification.json`。

验证边界：本轮没有安装真实 NeoECO，专用服对应原生回执测试跳过；NeoECO 新 API 仅完成接口替身合约验证。Mekanism Extras 无限反应工厂未安装，两项可选用例在每个机器环境明确跳过；缺少工业先锋的环境只执行 4 项 Mekanism 用例，未把 16 项工业先锋测试计为通过。新高级设置开关已覆盖服务端菜单操作、GuiSync 标识、NBT/记忆卡及方块更新流，尚未完成真实客户端的按钮点击和视觉验收。EMI 检查不代表该新开关的 GUI 验收；历史 QuickPlay、多人稳定性和规划器耗时问题仍保留原有限制。

证据目录：`D:/codex-builds/ae2lt-gto-realtest-20261019/upstream-port-20261021`。修改前已有工作树及索引分别备份在 `lt-before.zip`、`core-before.zip`；最终单元 XML 为 `lt-junit-results.zip`、`core-junit-results.zip`。

## 源码与制品

Core 仍在独立目录 `D:/codex-builds/thunderbolt-infinite-port-20261020` 工作，没有修改同级开发中的 Core。可从公开 `upstream/gto` 的 `7c7a066af9d404010fe0b9472fca687879b36281` 应用 gto.4 完整补丁（历史本地文件 `docs/patches/core-gto.4-from-upstream.patch`），包含此前 GTO 修复与本轮变更，共 77 个文件；基线正向和当前源码反向检查均通过。此补丁与旧版完整/增量补丁二选一，不能叠加。补丁 SHA-256：`c58a35320ca06d8a190fc0a6bbe8f80e66b8b2ae684b75a05c0ca3968b66d971`。

先在 Core 执行 `check jarJar verifyReleaseJar publishMavenJavaPublicationToMavenRepository`，再在 LT 执行 `check jarJar`，传入该 Core 仓库的 `-Pthunderbolt_local_repository=.../repo` 和实际 GTOCore 的 `-Pgto_core_jar=...`。可选 QA 使用 `-PgtoQa gtoQaJar gtoQaLtJar`，等待重混淆全部完成再部署。

`build/paired-release/` 保存本轮两个主 JAR；历史 gto.2/gto.3 制品保留。正式游戏仅放两个主 JAR，排除 QA 和测试代理。未提交或推送本轮修改。

## 最终配套成品

客户端与服务端同时更新以下两个文件；校验清单为 `build/paired-release/SHA256SUMS-gto.4.txt`。

| 文件 | SHA-256 |
| --- | --- |
| [ae2lt-forge-1.20.1-2.1.2-gto.4.jar](build/paired-release/ae2lt-forge-1.20.1-2.1.2-gto.4.jar) | `8e1ef03d0a6f318b7cef3284ff5710cffd7ec6bea2fa8703085e2f85e0637136` |
| [thunderbolt-reborn-forge-1.20.1-2.0.3-gto.4.jar](build/paired-release/thunderbolt-reborn-forge-1.20.1-2.0.3-gto.4.jar) | `ebcc77289d015b037d69637bbb699623d426127900dc6a7a6b1c907a791ed250` |

历史补丁仅作为本地归档保留，不随当前版本提交。重建当前配套版本请使用 [已清除 NeoECO 的 Core gto.5 完整补丁](docs/patches/core-gto.5-from-upstream.patch)；不要叠加历史补丁。
