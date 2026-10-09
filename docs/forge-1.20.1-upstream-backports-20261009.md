# Forge 1.20.1 GTL 上游修复移植（2026-10-09）

Forge 上游基线仍为 `1.20.1@877672e`。本次另外选择移植 `main@5d6c0da6` 与 alpha 的 `6c7ecf31`、`e65fa857`、`bbb20b0c`，按现有 Java 17 / Forge 47.1.47 / AE2 15.4.10 项目适配。

## 行为变化

- 时间轮将原生 Thunderbolt 批次容量限制传入本地分配事务。先解析并预留一份实际输入，准入后再提取其余份数；容量为零或回调异常时退回原型输入。已有替代输入分配、严格兄弟任务库存保护、共享种子与原料所有权规则继续参与事务。
- 批次后恢复空闲的供应器可继续接收普通单件工作。测试同时核对配套依赖的供应器过滤行为。
- 过载样板供应器拒收 Applied Flux FE，包括关闭导入过滤、显式 FE 输出样板、被动与 EJECT 入口。通过 key 类型 `appflux:flux` 和 ID `appflux:fe` 共同判断，其他同 ID 物品和流体仍可回收。
- AE2 15 的库存能力使用全局 `isAllowed`，因此返回库存也对外宣告拒收 FE。直接设置、存档恢复与已有 FE 排出不受准入过滤限制，避免删除或滞留旧缓冲。
- 同步 EMI/JEI 催化器模式、流体适用范围、催化剂和矩阵提示，使用 Forge FluidStack / ForgeTypes 与 Java 17 集合接口。同步中英文采矿工厂和过载 IO 端口指南。
- 新增七项 Forge 能源服务器回归及隔离运行入口，并加入已有全局压测命名空间。

## 配套依赖

旧 `2.0.0-beta.5` 不包含容量准入 API。本次使用包含 `PreparedBatch` 与 `currentBatchCapacityLimiter` 的 **Thunderbolt GTL 2.0.0**，坐标为：

`com.moakiee.thunderbolt:thunderbolt-reborn-forge-1.20.1-gtl:2.0.0`

该坐标与普通 Forge 构件分开，配套 GTL 分支保留规划注入、CPU 让位和 GTL 适配。运行依赖范围更新为 `[2.0.0,2.1.0)`；安装时需同时更新配套核心。GTL 发布流程仍从 Maven 解析专用构件，GitHub Packages 地址同步到 Reborn 仓库。开发者先在对应 Thunderbolt GTL 分支执行 `publishToMavenLocal`，再构建本项目。

本次验证固定的依赖 JAR SHA-256：`dd62094acda3c8586641bd8451e1cefd24a9c85a75c427a97260410497219b6a`。生产类使用 Java 17 字节码（major 61）。AE2LT 网络协议仍为 `gtl-10`。

## 验证

- 完整 JUnit 1463/1463 通过，无失败、错误或跳过。
- 自适应批次压力测试 6/6 通过。
- 发布脚本契约 13/13 通过。
- 能源专用真实 Forge 服务器回归 7/7 通过：相邻/无线物品和流体回收不吸走 FE、感应卡供能与拆卡停止、普通/扩展供应器所有模式 FE 拒收，以及旧 FE 缓冲排出。最终混合能源回归还覆盖旧 FE 缓冲的 NBT 写入、恢复与排出。
- 开发夹具编译与 `reobfJarJar` 成功；1435 个 JSON 可解析，refmap 完整，无重复归档项，开发能源夹具与 Thunderbolt 生产类未嵌入 AE2LT JAR。

- 合成、回填与机器能源兼容性服务器回归 80/80 通过。
- 导入/导出服务器回归 77/77 通过，覆盖物品与流体传输、缓存与守恒、回压恢复及连续吞吐。
- 最终新旧能源混合服务器回归 21/21 通过，服务器正常退出且 Gradle 构建成功。新能源夹具使用独立 Dropper 能力，避免与既有 Barrel 能源夹具覆盖。

首次混合运行有一个既有 Tesla 用例在 tick 40 报告 `test machine has no grid`，其余 20 项通过。将新能源模板从 40×4×40 缩小为实际需要的 8×5×8，并为七项新能源用例设置独立测试批次后，新世界复测 21/21 通过；既有用例的断言、时序和超时未修改。失败日志 `energy-mixed-final.log` 与通过日志 `energy-mixed-retry.log` 均保留。此结果证明调整后的夹具可以共同运行，未单独区分模板尺寸与批次隔离各自的影响。

验证证据位于忽略目录 `build/upstream-adaptation-20261009/`，包括原文件备份、仅本次改动的差异、固定依赖、测试日志和构件审计。首次编译的 JEI 文本插入位置错误已修正，失败日志保留。

复现（已发布配套 GTL 核心到 Maven 本地仓库，使用 Java 17）：

```powershell
.\gradlew.bat '-Dnet.minecraftforge.gradle.check.certs=false' test adaptiveBatchStress compileJdbJava reobfJarJar --offline --max-workers=1 --no-daemon --console=plain
.\gradlew.bat '-Dnet.minecraftforge.gradle.check.certs=false' -I scripts/provider-energy-output-test.init.gradle runInterfaceIoGameTestServer -x downloadAssets --offline --max-workers=1 --no-daemon --console=plain
.\gradlew.bat '-Dnet.minecraftforge.gradle.check.certs=false' '-Pae2ltInterfaceIoTestNamespaces=ae2lt_provider_energy,ae2lt_machine_recharge' runInterfaceIoGameTestServer -x downloadAssets --offline --max-workers=1 --no-daemon --console=plain
```

## 构件

- AE2LT：`build/libs/ae2lt-forge-1.20.1-gtl-2.1.2-beta.jar`。
- 配套核心：`build/libs/thunderbolt-forge-1.20.1-gtl-2.0.0.jar`。

AE2LT SHA-256：`4eec3a5db3c81402a7f4cfb00f024aca770be761589e605ac590fa538196f50c`。

## 验证范围

这是上述四项提交的选择性移植，不代表全部 alpha 功能已完成重新审计。EMI/JEI 有编译与资源覆盖，未进行新的交互客户端验证。完整 GTLCore 整合包未在本次服务器夹具中运行。本次未重跑三轮导入/导出性能测量，因此没有新的 MSPT 或性能预算通过结论。
