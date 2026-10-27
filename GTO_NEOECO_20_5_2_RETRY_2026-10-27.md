# NeoECO 20.5.2-gto.1 与 Core gto.6 实包复测（2026-10-27）

已用未修改的 NeoECO `20.5.2-gto.1` 正式附件和已发布的 Core `2.0.3-gto.6`，完成新版专服启动、NeoECO 专项和 AE2LT 整合回归。旧记录测试的是 NeoECO `20.5.0-gto.1`，不能代替本轮新版验证。源码基线为 `85cb1326d7a72effd06e41faa40358f9277abba0`，以下结果包含本轮本地修复，按项目约定整理为一个本地汇总提交；本轮未推送。

## 环境与制品

实际环境为 Minecraft 1.20.1、Java 21.0.10、Forge 47.4.20、GTO 0.6.0-dev-23、GTOCore 26.10.10、GTO GTM 26.10.70、AE2-GTO 15.2610.31、GuideME 20.1.15、LDLib 1.0.52.a、LT 2.1.2-gto.9。

NeoECO 上游 [20.5.2-gto.1 预发行版](https://github.com/Yang120231/NeoECOAEExtension/releases/tag/20.5.2-gto.1) 的说明面向 Dev25 / GTOCore 26.10.18 / AE2-GTO 15.2610.40。其实际加载元数据接受本轮 Dev23 依赖，专服已实际通过；这不表示本版 LT 已完成 Dev25 移植或验证。

| 制品 | SHA-256 |
| --- | --- |
| `ae2lt-forge-1.20.1-2.1.2-gto.9.jar` | `e1e4409ff0c1e4004a18604c7edfb2153bb0a15480d75520270c2d26375a75a7` |
| `thunderbolt-reborn-forge-1.20.1-2.0.3-gto.6.jar` | `85ae8b28afacd951d19af79a1ad8708d01f29e834e17d7e0448772ed4bbddd1f` |
| `neoecoae-20.5.2-gto.1.jar` | `c535ea3256a1ab5b0ad6025a28b5ead90e295b3fe7c2a1198246c1369a4d7f8b` |

NeoECO 主包和 sources 的大小、SHA-256 均与上游资产摘要及正式 `SHA256SUMS.txt` 一致，原件未修改。LT 保持现有版本号，须按哈希区分本轮制品与历史 `gto.9`。

## 本轮改动

- `src/main/resources/META-INF/mods.toml`：NeoECO 可选依赖接受 `[20.5.0-gto.1],[20.5.2-gto.1]` 两个精确版本，保留旧版。
- `build.gradle`、`NeoEcoPublishedFixtures.java`、`NeoEcoPublishedApiTest.java`、`NeoEcoRemoteUiTransformTest.java`：新增 `-Pneoeco_fixed_release=/path/to/neoecoae-20.5.2-gto.1.jar`，与旧实际包同时检查迁移锁、101 位数量及单次提交、完整 UI 协议。显式配置的文件纳入 Gradle 输入，缺失会失败；新版受保护 UI 字节码必须原样保留。
- `LoopSeedLedgerBook.java`、`Ae2LtTimeWheelCraftingCpuLogic.java`：全检发现模糊种子竞争等待环。迟到的物理回料先偿还同一逻辑种子的变体借款；成功派发后，仅以实际在库、归当前消费者且不与其他逻辑种子冲突的完整种子批次结清借款，释放等价公共库存。不修改物理数量或其他消费者的归属。
- `LoopSeedLedgerBookEdgeCaseTest.java`、`LoopSeedAllocationLivenessTest.java`：保留失败断言，增加迟到回料、在库归属、其他消费者、重叠种子与完整批次边界，以及同一竞争调度重复 64 次的回归。
- 更新 `GTO_COMPATIBILITY.md` 和本记录；未改动 Core、NeoECO 源码或 `AGENTS.md`。

原全检在 `LATE_FUZZY_VARIANTS, batch=true, schedule=17` 出现真实等待环；仅单独重跑曾通过，不能消除问题。重复分配回归及四个迟到借款用例在修复前失败，修复后定向 226 项通过，随后完整重跑。初次全检还遇工作盘空间不足，后续通过临时构建目录、独立 Gradle 项目缓存和隔离专服继续，保留原目录及全部失败证据。

专服重跑中，一次精确数量组 JVM 在启动完成前退出，原因未确定；另一次批量组的 5 项行为测试全部通过，但规划器启动探针触发 10 秒 `TimeoutException`，该次按整体失败处理并保留原世界和日志。后续以相同冻结制品、新世界重新执行，按三项探针、预期数量及正常保存退出逐项验收。不能据最终通过推定整包在任何负载下都满足该启动时间边界。

## 结果

| 检查 | 通过 | 跳过 | 失败/错误 |
| --- | ---: | ---: | ---: |
| JUnit，共 1595 项 | 1590 | 5 | 0 |
| 自适应批量压力测试 | 6 | 0 | 0 |
| 发行流程契约 | 12 | 0 | 0 |
| 安装新版 NeoECO 的 GameTest | 122 | 0 | 0 |

JUnit 跳过 4 项缺少 EAEP 实际包的用例和 1 项依赖 ModLauncher 环境的原生 CPU 用例。新版实际 JAR 的三项检查均已执行，通过结果不来自跳过。构建 `build adaptiveBatchStress jarJar gtoQaJar gtoQaLtJar -PgtoQa` 及最终 `check` 完成，另运行 `python -B .github/scripts/test_release.py`。使用本地离线依赖缓存与临时初始化脚本，未修改项目仓库配置。

| 专服套件 | 通过/预期 |
| --- | ---: |
| special | 2/2 |
| base | 11/11 |
| exact | 19/19 |
| batch | 5/5 |
| dispatch | 20/20 |
| permissions | 5/5 |
| interfaces | 6/6 |
| io | 40/40 |
| infinite | 1/1 |
| content-recipes | 13/13 |

每组使用新世界和独立 JVM，均满足 CPU、STORAGE、PLANNER 三项 PASS、精确预期数量、每项 GameTest PASS、零启动失败、正常保存及退出。NeoECO 专项覆盖真实 F4 主机批量接收、工作线程 NBT 往返与取消回收，以及正式 facade 的精确数量、同步回流和拒收。

最终正式主包分别在安装新版 NeoECO、保存后重启同一世界、移除 NeoECO 三种情况下通过加载和桥接探针，正常保存退出。启动探针的 `total=0` 不计入 GameTest 数量，也不证明正在执行的合成任务已通过跨 JVM 恢复。

QA 包的 2138 个生产类与正式主包逐字节相同。正式包的精确依赖元数据、4 份 Mixin 配置与类/refmap、1484 份 JSON/mcmeta、许可证和嵌套 JAR 校验通过；不包含一次性 QA 夹具。相对上一轮正式主包，归档变化仅涉及依赖元数据、清单及两处时轮源码的类族（含调试行号变化），没有新增或移除归档项。

## 边界与证据

本轮未执行图形客户端远程 UI、独立客户端多人联机、真实 FX 虚拟网络、正在执行的合成任务跨 JVM 恢复或 Dev25 环境。整合包未安装 Industrial Foregoing、Mekanism、Useless Mod，相应可选机器套件未覆盖。

日志仍保留第三方 Mixin 缺少 `minVersion`、LDLib 客户端注解扫描及其他整包诊断；本轮断言通过不表示日志零报错。逐组诊断已单独归档。

本地证据入口为 `build/neoeco-fixed-retry-20261027/verification.json`，包含实际版本、各制品哈希、逐组结果与边界。冻结原件、源文件快照、修复前失败、最终测试 XML、构建记录、逐次 mods 哈希和原始专服日志均保留；大体积运行数据位于独立临时构建位置，位置记录在忽略的本地证据中。日志、世界、制品和本机路径不加入仓库。
