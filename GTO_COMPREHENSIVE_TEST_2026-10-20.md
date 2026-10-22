# GTO 配套版本全面测试（2026-10-20）

**本轮并非全部通过。** LT 单元测试、88 项专用服用例、磁盘数量核对和正常启动 EMI 通过；Core 全检仍不稳定，QuickPlay 下的 EMI 重复配方注册失败仍未解决。正式包联机一次入服成功，修正测试身份后的对照运行却在登录阶段超时，不能视为稳定通过。定向复测通过不替代这些失败记录。

对象为 LT `2.1.2-gto.2` / Thunderbolt Core `2.0.3-gto.2`，使用 GTO 0.6.0-dev15、Minecraft 1.20.1、Java 21、Forge 47.4.20、GTOCore 26.10.5 及其内嵌 AE2-GTO 15.2610.8。测试只使用独立 GTO 实例和 QA 存档；没有修改 GTOCore、GTOLib、内嵌 AE2 或旁边的 Core 开发目录。

正式制品保持上一轮移植成品：

| 制品 | SHA-256 |
| --- | --- |
| LT `2.1.2-gto.2` | `86a56e314d060b97daf6e9c3d88096cdc1fca94f97fce4c443140880b0598168` |
| Core `2.0.3-gto.2` | `0b9a5f330b24fd8ee539515204b81683cd54d7915b26e3ca49ada220d620b9cb` |

本轮测试记录根目录：`D:/codex-builds/ae2lt-gto-realtest-20261019/comprehensive-20261020`。下表中的专用服结果均来自本轮新世界，不合并历史通过次数。

最终总汇为该目录的 `comprehensive-results.json`，明确记录 `testing_complete=true`、`all_checks_passed=false`。本轮创建的客户端和专用服进程均已退出；没有停止其他开发目录的进程。

QA 辅助文件也按运行阶段记录，不能把后续构建的哈希套用到早期日志：

| 辅助文件 / 使用阶段 | SHA-256 |
| --- | --- |
| instrumented LT，七组服务端中六组及终端/EMI 客户端 | `578a3661d9bf6c4818a849fe21a16a1e16d41123bba2c346a790e3aa7ef9d341` |
| 初始 QA helper，七组服务端及初始三个客户端 | `7421dbd86bd8e286860b68fbad6bc138fe53fd0acdbb16f29195435054d300f9` |
| 增加延后进世界后的 QA helper，正常启动 EMI 及独立联机 | `6346108ef2f2246e70fc475c5b453a2f5c7bfa778d22bb746965d96a3c85e8ec` |

`content-recipes` 与客户端 smoke / 独立联机使用正式 LT。instrumented 包仅附加可选夹具，主包的非 manifest 文件逐项一致；所有阶段使用同一正式 Core。

## 服务端结果

| 测试组 | 通过 | 主要覆盖 |
| --- | ---: | --- |
| `base` | 11 | 实体 CPU 超载投料、暂停、存档、回料、拆除与防重复回收；时轮、共享种子闭环 |
| `exact` | 13 | 大数库存及订单、混合供应器、实际等待与回料、存档恢复、取消退款、long 分批 |
| `interfaces` | 6 | 接口导出公平性、无线工厂补货和实际采矿工厂处理 |
| `batch` | 4 | 批量结算、部分接收、退款及不确定所有权 |
| `io` | 40 | 过载 IO、矩阵、过滤器、菜单、存取预算、容量边界与回滚 |
| `infinite` | 1 | 八元件实际路由、独立总账、超大数量、摘要与 UUID、通道单位变化、SavedData 编解码 |
| `content-recipes` | 13 | 配方材料、网络往返、机器大堆叠存档及配方执行 |

合计 **88 项通过，0 项失败**；未安装 NeoECO 的 2 项另记跳过。七轮各自的 CPU、STORAGE、PLANNER 探针均通过，服务端进程均正常退出 0。`content-recipes` 使用正式 LT 主 JAR，其余使用附加可选夹具的 instrumented LT；生产代码字节一致。

各组日志为 `server-组名.log`，汇总为 `server-results.json`。压力基准默认关闭，88 项不包含重复的启动探针。

## 磁盘存档核对

服务端退出后，独立 Python NBT 读取器直接解压本轮 `infinite` 世界的 `thunderbolt_indexed_cells.dat`，不使用 Java 内存对象或 Core 自身的解码方法作为数量判据。

- 9 个 UUID 库存，含 8 个活动元件及 1 个空闲库存；磁盘上有 77,831 个非空条目。
- 合并后 45,063 个唯一键：45,056 种带 NBT 的物品及 7 个混合键。
- 根据独立的初始种子规则逐键核对数量，总量为 `49,159,114,688`，没有丢失或重复。
- 对每种 NBT 物品根据 Java Random 算法重建并比较完整的 1 KiB 负载，同时检查每个库存的 `totalTypes`。

结果为 `infinite-disk-check.json` / `.log`。这是实际压缩存档的内容验证，不能替代任意用户旧存档迁移或长期运行验证。

## 单元与静态检查

LT 重跑 `check test --rerun`：**1,495 项，1,490 通过、5 跳过、0 失败**。日志：上一级 `comprehensive-lt-check-20261020.log`。补齐测试代理 ASM 后再次完整重跑，数量和结果一致；日志：`comprehensive-lt-check-agent-fixed.log`，8 分 36 秒，退出 0。

Core 本轮完整重跑**未达到零失败**，不能用定向通过覆盖全检失败记录：

| 运行 | 主测试总数 | 通过 | 跳过 | 失败 |
| --- | ---: | ---: | ---: | ---: |
| 原 C 盘临时目录 | 1,521 | 1,502 | 13 | 6 |
| D 盘临时目录，与游戏并行 | 1,521 | 1,504 | 13 | 4 |
| D 盘临时目录，关闭本轮游戏进程 | 1,521 | 1,505 | 13 | 3 |

首次的 6 项失败为两组直接 OR-Tools 调用的原生方法链接错误；C 盘临时目录存在未解压完整的 DLL，当时可用空间不足 200 MB。通过外部 Gradle init 脚本仅将测试 `java.io.tmpdir` 改到 D 盘后，原生链接错误消失。另有 23 项冷启动/回放测试在首次检查中通过，不与后续主测试结果拼成一次全检通过。

第二次的 4 项失败涉及 `CpSatComplexCycleAdversarialTest`、`CpSatExecutionBlocksTest`、`CpSatSparseRankedTest` 和 `ExternalPlannerRecursionComparisonTest`，在第三次完整重跑均通过。第三次有 `PlanningCandidateExecutorTest` 的两项取消/隔离时间断言失败，以及 `TerminalBatchRecoveryTest.publicMultiUnitRecoveryRefusalMarksCutoffAndPreservesTheCoreMissingWitness` 预期已消费预算 1、实际为 0。后者处在 100 ms 可选恢复窗口内，本次耗时 141 ms，可能在预算消费前到期，但目前没有证据确认唯一根因。

随后筛选 `PlanningCandidateExecutorTest`、`TerminalBatchRecoveryTest` 和 `cpsatbridge.*` 的定向重跑 **48 项全部通过**。没有降低阈值、增加规划预算或改写生产规划器；仍保留完整测试不稳定的结论。相关全检日志为上一级 `comprehensive-core-check-20261020.log`、`comprehensive-core-check-d-temp.log`、`comprehensive-core-check-quiet.log`；定向日志为 `comprehensive-core-targeted-after-agent-fix.log`，失败 XML 分别保存在本目录三个 `core-*-failures` 子目录。

筛选测试还暴露测试代理的独立依赖缺陷：Gradle 安装测试类加载器之前执行 `premain`，代理 JAR 未携带 ASM，会抛出 `NoClassDefFoundError: org/objectweb/asm/ClassVisitor`。已在 LT 和独立 Core 的 `gtoTestAgent` 任务中仅为测试代理打包 ASM，Core 定向重跑验证通过；正式游戏 JAR 未改变。

静态检查：

- 5,203 处外部字段/方法引用、484 个目标类；已安装依赖缺失成员为 0。37 处引用的目标类未安装，涉及 AdvancedAE、Applied Flux、AE2AddonLib，未算作集成通过。
- 1,866 个具体 LT 类，缺失抽象方法实现为 0。
- 正式 JAR 中 1,435 个 JSON 均可解析。中英文各 1,538 个唯一语言键，未发现缺键或实际格式占位符差异。
- `jei.ae2lt.crystal_catalyzer.matrix_note` 在两份语言文件内各重复一次，同语言的值一致，保留为冗余记录。

语言扫描最初把英文百分比后面的文本误判为格式参数，已修正扫描器；未修改游戏翻译来消除误报。

## 客户端

- 真实客户端进入隔离世界并渲染，精确数量格式作用域和界面变换检查通过。
- 93 条启用 Mixin 配对涉及的 73 个目标类加载通过，8 条可选项跳过。结果：`smoke-mixin-result.txt`。
- 原生与天枢终端的 `10^100 + 12345` 库存、真实提示完整数值、减至 37 后的缓存清理及恢复精确值均通过。截图：`exact-terminal-0.png`、`exact-terminal-1.png`，结果：`stock-result.txt`。
- 初次 EMI 交互检查失败：GTO 数据加载重复注册 `gtceu:liquefaction_furnace/extract_air`，使 EMI 配方表构建失败。LT 插件已被 EMI 初始化并加载；不能将此失败记为 EMI 兼容通过。
- 正常主菜单启动对照**通过**：QA 使用可选 `-Dae2lt.gtoQaWorld=GtoQA`，在主菜单 400 tick 后打开相同隔离世界。实际 EMI 配方注册、配方界面、控件边界、旋转、缩放和外壳切换通过，进程退出 0。结果：`emi-normal-results.json` / `emi-normal-result.txt`，日志：`client-emi-normal.log`，本轮截图：`emi-normal-multiblock.png`，已人工查看。
- QuickPlay 的失败保留为未解决的启动时序兼容问题；正常启动成功不能证明 QuickPlay 已修复。两次运行更换了 QA 启动方式及测试临时目录，尚不足以把根因限定为其中某一项。没有修改或屏蔽 GTO 的配方注册逻辑。
- 正式 LT/Core 配套的独立客户端成功连接新建专用服世界，实际渲染、精确数量格式作用域和界面变换检查通过，73 个 Mixin 目标类检查通过，两端退出 0。日志：`server-multiplayer.log` / `client-multiplayer.log`；结果：`multiplayer-results.json`；截图：`multiplayer-client.png`。
- 该次联机的启动参数使用固定 UUID，与离线服务端根据 `GtoQA` 名称生成的 UUID 不一致，日志出现 FTB Teams 玩家身份错误。将 QA 启动参数改为 Java 离线 UUID 规则生成的同一身份后，在另一个新建世界对照：客户端连接约 32 秒后收到 `disconnect.timeout`，没有入服；服务端记录未完成登录的 `GameProfile[id=<null>,name=GtoQA]` 断开。两端退出 0，但断言失败，监督进程退出 1。结果：`multiplayer-identity-results.json`，日志：`server-multiplayer-identity.log` / `client-multiplayer-identity.log`。根因尚未确认，不能把它直接归因于 UUID 修改或 LT，也不能算作联机稳定通过或 FTB Teams 集成通过。
- 这次超时运行的监督结果中 `outcome` 读到了前次残留的 Mixin `PASS` 文件；本次未执行世界内断言，其整体 `passed=false` 正确，不能把残留字段计为通过。监督脚本现已改为每次启动前备份并清除各类旧结果文件。原 JSON 保留原貌，此说明及总汇单独标记残留字段。
- 联机 smoke 在界面断言通过后立即退出，没有等待 EMI 后台索引完成；退出后的 `MoremorelangClientForgeEvent.onItemTooltip` / EMI 搜索烘焙空指针及 ModernUI dead-thread 日志保留。该运行仅覆盖入服与界面/Mixin，不据此宣称联机 EMI 完整通过。上述 QA 启动变更仅在可选源集内。

当前客户端原始记录：`client-smoke.log`、`client-stock.log`、`client-emi.log`、`client-results.json`。库存截图底部亦可见 EMI 错误，因此库存通过结论只覆盖库存与终端检查。

## 制品与补丁核对

最终核对文件为 `final-integrity-results.json`：正式 LT/Core 的 SHA-256 保持本文开头的值；instrumented LT 中 4,195 个正式非 manifest 文件逐字节一致；正式包不包含 QA 主类或无限存储测试夹具。移植前已修改的两个其他 LT 游戏测试文件哈希保持不变。两个测试代理 JAR 均确认包含 ASM 的 core、commons、tree 类。

Core 补丁包含九个移植文件及本轮测试代理构建修复，共十个文件；在独立基线索引正向应用和当前工作树反向应用检查均通过。补丁 SHA-256：`51bb05ec91396fc204db3bb57f79b06959a76a38390f493f191cd9ecc9aba511`。本轮没有变更正式生产代码，也没有重新发布制品。

## 范围

通过断言不表示整合包日志没有错误。原始日志仍记录部分其他模组的缺失模型/贴图、FancyMenu 资源枚举失败及 GTO 引用不存在的 Ad Astra globe 物品；本轮没有修改这些模组，也未将其各自功能算作通过。

未穷举未安装的附属、所有配方批量组合、长期多人压力和用户旧存档迁移。默认 GTO 使用 EMI/JEMI，本轮不将其结果扩展为独立原生 JEI 环境通过。未测试外置 AE2-GTO 15.2610.10 与此 GTOCore 的混用，也没有 FPS/TPS 或吞吐量提升结论。
