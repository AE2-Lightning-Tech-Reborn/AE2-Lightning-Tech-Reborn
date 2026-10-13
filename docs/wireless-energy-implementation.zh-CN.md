# 无线能源第一切片与验收记录

目标：Minecraft 1.21.1 / NeoForge。独立功能分支 `feature/wireless-energy-ledger-20261013` 基线为核对后的远端 main `5269dcabdd52acd95471ab373587d0ea4758ebcb`。用户主工作区本地 main `1bda5091` 及未提交改动未带入、未修改。机制依据 docs 分支 `0e20b7caece0ddc79d0f49603ca1847aec85cdf8` 的 `无线过载控制器能源/设计方案.md`。仅在本地提交，无推送、PR、合并或发布。

## 用户澄清与本切片边界

2026-10-13：用户确认原机制设计基本不变，待讨论主要是 **UI 查看消耗、管理链接**。不因 UI 未定搁置机制，也不在此分支定稿最终 UI。手动连接不做真实准入探测；自动 FE 准入必须后续实现最多 100 FE 且等待目标实际机器 tick，本切片不启用。未接入 FE→AE，未来也只支持运行网络，不支持冷启动。

## 安全归属

- 已提取未送出的 FE 进入世界 SavedData 账本；AE 节点保存账户 UUID。网格分合通过 AE2 saveNodeData/addNode 移交 UUID，绝不把整网余额复制到每个控制器。
- 余量属于提取节点的托管账户。拆分时随该节点归属，不按网络数量均分；合网不会合并/复制账户余额。与真实 FE 库存始终分开。
- 退款拒绝保留余额，下一次合法输送优先消费已有余额。节点正常卸载/重启恢复账户；永久移除仍保留世界账本记录，不能让同坐标新放置方块自动继承。
- 对异常接收回调无法证明对方是否已收电，金额隔离为 uncertain，禁止自动重发。管理员后续恢复界面需展示该状态，不能默默退款。
- 正常世界保存/退出是恢复边界。Minecraft 的多个存档文件并非事务数据库，不声称应对任意断电/半写盘下的跨模组恰好一次交付。

## 端点与调度

服务端记录频率、维度/坐标、输入面、绑定人、实例 UUID、方块和 BE 类型；不写第三方机器 NBT，不创建 AE 节点。通过 LevelChunk 实际移除 BE 时撤销旧实例，区别卸载和同类型替换。每次有限轮询重新取能力，不持有跨卸载失效的能力。手动绑定仅查看能力是否支持输入，不调用模拟或真实接收；正常运行才发送 FE。

共享网格输出预算、目标预算和全局访问预算均有界。数值为可配置工程初值，非最终平衡承诺；默认每网每 tick 65536 FE、每目标 4096 FE、每网 32 次访问、全局 128 次访问，每频率 256 端点，全球 8192 端点。留在 ME 库存的 10000 FE 仅约束此出口，不是 FE→AE 代理或对其他模组出口的保电承诺。预算在回调前扣除，拒收也占此次尝试额度。发生分合网的当个 tick 暂停外供，避免通过拓扑变更获得新一份预算。

## UI 待讨论（机制接口保持可替换）

1. 消耗展示：区分实际送达 FE、尝试预算、账本待退 FE、隔离 FE；显示瞬时速率还是窗口平均/累计、放在哪个页面。`inspect` 返回独立端点状态、最近访问 tick 和该次实际交付量，不把预算当消耗。
2. 链接管理：设备列表如何呈现 AE 和 FE 两项连接、绑定面、离线/权限失效/故障状态；单条/批量解除按钮与筛选。底层 `setBound(..., desiredState)` 为幂等接口，未来 UI 不需要模拟卡片 toggle。
3. 移除控制器后的托管账户如何供有权限玩家查看和认领；不默认为同坐标新方块继承，也不做清零。异常回执需人工核实，不承诺一键安全恢复。

## 验证记录

2026-10-13 最终实现工作树的结果（测试源码位于 `src/test` 和 `src/jdb`；GameTest 不打入产品 jar）：

| 验证 | 结果 | 覆盖与限制 |
| --- | --- | --- |
| Gradle `build` | 通过 | 编译、打包、全量 JUnit；1484 项中 1480 通过、0 失败、4 跳过。4 项为未配置 `ae2lt.eaepTestJar` 的既有 EAEP 实包契约测试。 |
| `EnergyEscrowTest` | 6/6 通过 | 拒退/部分接收、异常回执隔离、溢出边界、NBT 规范账户、固定种子 10000 次守恒、共享预算。包含于上述 JUnit 总数。 |
| `WirelessEnergyGameTests` | 12/12 通过 | 不探测绑定、重复绑定、权限拒绝与运行中撤权/恢复、伪造点击拒绝、真实 FE 输送、同类型替换撤销、能力失效重取、卸载回调保留绑定、维度离线、拒退/回调重入/异常回执、来源脱网、节点归属转移。实际移除和恢复连接方块后，两台控制器的 31/73 FE 各自保留，合网不复制。 |
| `WirelessEnergyRestartGameTests` | 两个独立 JVM 各 1/1 通过 | prepare 正常保存退出，verify 从同一世界恢复 7331 FE 可用余额、17 FE 隔离余额和真实控制器节点的 991 FE 账户；账户 UUID 通过 AE 节点 NBT 恢复。 |
| 既有 Provider 能源输出/回流过滤 GameTest | 11/11 通过 | 邻接/无线感应输出、移除后停止、既有 FE 回流缓冲、物品/流体回流过滤。 |
| `git diff --check` | 通过 | 无空白错误。 |

所有 GameTest 均核对实际注册数及 `All N required tests passed`，随后正常停服保存；没有遗留游戏或专服进程。遵守共享测量静默窗口，未开展性能基准。

### 复现与依赖边界

Java 21，Gradle 8.8。初始离线构建因发布依赖 `thunderbolt-reborn:2.0.3-beta2` 未缓存失败。本轮用同机 Thunderbolt 实现任务构建 jar 的独立副本，通过本地 init script 替换该依赖，**没有修改产品依赖声明**。副本 SHA-256 为 `ec766f0ce0505b8c49c3dd31cff908f99db74624b35c988b788b5b8634da62e4`。本轮结果不能替代针对最终发布版 Thunderbolt 的集成验收。

本地证据保存在未提交的 `build/verification/`，含执行日志、JUnit XML、注入脚本和产物散列；原始开发日志不入库。已构建的本地验证 jar 为 `build/libs/ae2lt-2.1.2-beta3.jar`，不作为已发布版本。

```sh
# local-dependencies.init.gradle 只替换尚未缓存的 Thunderbolt 依赖。
./gradlew -I build/verification/local-dependencies.init.gradle build --offline --no-daemon --no-configuration-cache --max-workers=2
./gradlew -I build/verification/local-dependencies.init.gradle -I scripts/wireless-energy-test.init.gradle runGameTestServer --offline --no-daemon --no-configuration-cache --max-workers=2
# 以下两条必须按顺序、使用同一个隔离测试世界运行。
./gradlew -I build/verification/local-dependencies.init.gradle -I scripts/wireless-energy-test.init.gradle -PwirelessRestartPhase=prepare runGameTestServer --offline --no-daemon --no-configuration-cache --max-workers=2
./gradlew -I build/verification/local-dependencies.init.gradle -I scripts/wireless-energy-test.init.gradle -PwirelessRestartPhase=verify runGameTestServer --offline --no-daemon --no-configuration-cache --max-workers=2
./gradlew -I scripts/provider-energy-output-test.init.gradle -I build/verification/local-dependencies.init.gradle runGameTestServer --offline --no-daemon --no-configuration-cache --max-workers=2
```

### 已修正的失败与未验证项

- 首次 GameTest 命名空间过滤错误导致 0 注册测试，虽 Gradle 成功也不计通过；修正为模板实际命名空间后运行全部测试。
- 曾将精简 GameTest runtime 用于全量 JUnit，导致既有 JEI/AdvancedAE 集成测试无法发现；全量构建改用完整 runtime 后通过。
- 新增远处控制器重启夹具初次因区块未 ticking 而未创建节点；测试使用临时强制加载、结束释放后，两个 JVM 均通过。该失败不被当作通过证据。
- 精简 GameTest runtime 不含 ExtendedAE，日志有既有 `ae2lt:assembler/overload_processor` 的 `extendedae:config` recipe codec 警告；不声称整个可选模组组合无警告。
- 卸载测试验证实际 `LevelChunk.clearAllBlockEntities` 回调及 NBT 重建；未运行完整区块管理器的磁盘卸载/重载压力测试、长时服务器测试或客户端手工交互验收。不声称任意崩溃断电下的跨模组原子交付。
- 当前普通目标仅原生 NeoForge FE 能力。自动 FE 准入、非 FE 原生能源桥、Applied Flux 补 AE 均未实现；不提前设计冷启动或未定玩法。待讨论 UI 和孤立账户人工恢复入口见上文。
