# 无限存储上游移植（2026-10-20）

配套版本：LT `2.1.2-gto.2`、Thunderbolt Core `2.0.3-gto.2`。保持 Minecraft 1.20.1、Forge 47.4.20、GTOCore 26.10.5 及其内嵌 AE2-GTO 15.2610.8；客户端与服务端需一起替换 LT/TB。

## 来源与改动

- LT：`upstream/perf/infinite-cell-reviewed` 的 `19fe1d8b67e68080e48bd671fa579cdf3b4c3798`。
- Core：`12dc97f1df3ed676c4c2f916559976cdea08896d`。
- LT 读取提示摘要时直接读取 Forge NBT，避免复制整个标签。新增只读 `readSummary`，新建空壳不会因查询而产生 NBT。
- Core 普通存取使用原始整数路径，遇到真正的数量溢出再进入精确大数路径；按通道缓存计数和余数，检测通道单位变化后重算字节摘要。
- Core 缓存服务端 SavedData 实例，在服务端停止时清除引用；保存快照分离可变数量数组，复用稳定键数据，并覆盖旧快照在后续存取、槽位复用和压缩后的独立性。
- 保留 GTO 已有的有限元件容量边界、126 位旧格式边界、异常存档修复及重复键合并行为。未导入 1.21 DataComponents、Codec 或 NeoForge 异步文件写入机制。

`ae2lt:types`、`ae2lt:bytes`、`ae2lt:cell_id` 与世界 SavedData 格式保持不变。没有移植无线功能、Pigmee 或 MEST。

## GTO 测试适配

新增可选 `infinite` 游戏测试组，使用真实供电 AE 驱动器挂载八个无限元件：主元件 16,384 个带 1 KiB NBT 的键及七个混合键，其余七个元件各 4,096 个键，合计 45,063 个活动键；另有 4,096 键的空闲世界库存。

覆盖真实网络存取、模拟操作、1,024 个键的加入与移除、八元件路由、UUID 隔离、独立总账核算、超出 126 位的数量往返、逐元件摘要和 SavedData 编解码，以及物品复制/NBT/网络往返。

GTO 路由可能向一个元件插入、从另一个元件取出。测试先核对预先计算的全网独立总账，再冻结路由后的逐元件归属，验证其后保存重载没有丢失或重复。

GTO 基线没有安装 Applied Flux，因此以注册的测试通道模拟 `amountPerByte` 从 8 改为 17 再恢复，检查现有 handler 的重算。注册在 Forge `RegisterEvent` 中完成。实际混合键还包含物品、流体及 LT 闪电键。本次不能据此宣称实际 FE/GTEU 附属已通过测试。GTO 的原生物品键使用规范化实例和身份相等，相关 JUnit 按该语义适配。

## Core 独立工作目录与补丁

Core 工作目录：`D:/codex-builds/thunderbolt-infinite-port-20261020`。它以 Core `7c7a066af9d404010fe0b9472fca687879b36281` 为父提交，并保存开始移植时已有的 GTO 工作树改动，形成独立基线快照 `ba3496623b3510a44ba0b4047b40f0499721a984`。没有覆盖同级 Core 开发目录。

Core 补丁（历史本地文件 `docs/patches/infinite-core-gto.patch`） 包含相对该基线的九个移植文件，以及后续全面测试补充的测试代理 ASM 打包修复，共十个文件，不包含基线中已有的其他开发改动。它不是普通上游 Core 分支的通用补丁；在其他基线应用前必须检查差异。独立目录保存完整可构建源码。

在该 Core 目录执行：

```powershell
.\gradlew.bat check jarJar verifyReleaseJar publishMavenJavaPublicationToMavenRepository --offline --no-daemon --console=plain --max-workers=1
```

在 LT 目录执行（GTOCore 本地路径按实际环境填写）：

```powershell
.\gradlew.bat check jarJar gtoQaJar gtoQaLtJar -PgtoQa '-Pthunderbolt_local_repository=D:/codex-builds/thunderbolt-infinite-port-20261020/repo' '-Pgto_core_jar=/path/to/gtocore-forge-1.20.1-26.10.5.jar' --offline --no-daemon --console=plain --max-workers=1
```

在隔离 GTO 专用服使用 `-Dae2lt.gtoQaSuite=infinite`。QA 所需 instrumented LT 替代正式 LT，正式游戏使用主 JAR。该组会注册测试通道，请勿在正常游戏存档运行。

## 验证

本节为移植阶段的历史结果。随后针对相同正式制品的全面重跑发现 Core 全检不稳定和 QuickPlay 启动下的 EMI 数据加载失败，不能据本节推断当前全部检查通过；最新结果与保留的失败证据见 [全面测试记录](GTO_COMPREHENSIVE_TEST_2026-10-20.md)。

旧 Core `2.0.3-gto.1` 的对照运行在相同测试下失败：`Existing handler retained stale channel units`；CPU、STORAGE、PLANNER 三个探针通过，服务端正常退出。原始日志：`server-infinite-port-baseline4-console.log`。

- Core 完整 `check`：1,544 项，1,531 通过、13 跳过、0 失败（主测试 1,521 项，另有 23 项冷启动/回放测试）。`jarJar`、`verifyReleaseJar`、本地 Maven 发布通过。日志：`infinite-core-full-check.log`；统计：`infinite-core-check-results.json`。初次全检有一项规划器冷启动测试超过原有 1 秒阈值；未修改规划器或放宽测试，最终完整检查在原阈值下通过。
- LT 完整 `check`：1,495 项，1,490 通过、5 跳过、0 失败。正式及 QA JAR 打包和重混淆通过。日志：`infinite-lt-final-build.log`。
- 正式 LT 的所有非 manifest 条目与 instrumented 测试包逐字节一致；新加的可选 IO/无限存储 QA 夹具不在正式包内。移植前已修改的两个其他 LT 游戏测试文件哈希保持不变。
- Core 补丁已分别通过基线索引的正向应用检查和当前移植工作树的反向检查。

- 完整 GTO 专用服的 `infinite` 组：`PASS total=1 failed=0 startupFailures=0`，CPU、STORAGE、PLANNER 三个探针通过，服务端及监督进程均退出 0。日志：`server-infinite-port-paired-final2-console.log`。同一测试在旧 Core 下失败、新 Core 下通过，保留了对照证据。
- QA 辅助模组的依赖版本改为根据项目版本生成，避免升版后仍要求旧 LT；修正后已验证 JAR 元数据和实际服务端加载。日志：`infinite-qa-version-build.log`。

- 同一配套版本的 `io` 组：`PASS total=40 failed=0 startupFailures=0`，CPU、STORAGE、PLANNER 三个探针通过。日志：`server-infinite-port-io-final-console.log`。

## 正式制品

两个配套主 JAR 已集中到 LT 的 `build/paired-release/`，同目录有 `SHA256SUMS.txt`：

| 文件 | SHA-256 |
| --- | --- |
| `ae2lt-forge-1.20.1-2.1.2-gto.2.jar` | `86a56e314d060b97daf6e9c3d88096cdc1fca94f97fce4c443140880b0598168` |
| `thunderbolt-reborn-forge-1.20.1-2.0.3-gto.2.jar` | `0b9a5f330b24fd8ee539515204b81683cd54d7915b26e3ca49ada220d620b9cb` |

最终制品与夹具校验见 `infinite-port-final-verification.json`。正式游戏仅放两个主 JAR，移除同 mod ID 的旧版本；本轮未重新运行图形客户端。

所有原始日志保存在 `D:/codex-builds/ae2lt-gto-realtest-20261019`。这是功能正确性回归验证，未测 TPS、吞吐量或性能提升比例，也不等于整个整合包全部附属和玩法的穷举验证。

历史补丁仅作为本地归档保留，不随当前版本提交。重建当前配套版本请使用 [已清除 NeoECO 的 Core gto.5 完整补丁](docs/patches/core-gto.5-from-upstream.patch)；不要叠加历史补丁。
