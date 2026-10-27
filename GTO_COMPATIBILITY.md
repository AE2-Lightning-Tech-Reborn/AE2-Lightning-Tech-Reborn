# GTO 1.20.1 兼容构建

本分支面向 Forge 1.20.1 GTO，普通 `1.20.1` 分支用于同版本对照。按用户授权保留并维护指定 **NeoECO GTO 版**的可选兼容；以前的 NeoECO 排除记录只描述旧版本。本地开发约定已按授权同步更新。

## 当前配套版本

当前 LT `2.1.2-gto.9` 的最低支持整包基线为 **GTO `0.6.0-dev-23`**，已验证的是该官方发行包的配套环境。低于此基线的整包不在本版支持范围内；更新的整包仍需核对下表依赖，不能仅凭版本号更高判定兼容。

| 组件 | 版本 |
| --- | --- |
| Minecraft / Java / Forge | 1.20.1 / 21 / 47.4.20 |
| GTO 整包基线 | 0.6.0-dev-23 官方服务端发行包 |
| GTOCore | 26.10.10 |
| AE2-GTO | 15.2610.31 |
| Thunderbolt | 2.0.3-gto.6 |
| AE2LT | 2.1.2-gto.9 |
| NeoECO（可选） | 20.5.0-gto.1，`1.20.1-GTO` 分支 |

客户端与服务端一起更新整套依赖及配套 TB/LT，移除相同 mod ID 的旧包。该组合不能与旧 GTOCore 26.10.5 / AE2 15.2610.8 混用，也不适用于普通版 AE2 或普通版 NeoECO。LT 的加载元数据会明确阻止版本误装。NeoECO 本身仍为可选依赖。

加载约束为 GTOCore `[26.10.10,26.10.11)`、AE2-GTO `[15.2610.31]`、Thunderbolt `[2.0.3-gto.6]`；安装 NeoECO 时要求 `[20.5.0-gto.1]`。这里的最低整包基线不表示这些依赖可以任意升级。旧 Core `2.0.3-gto.1` 不适用于当前 LT。

本次实现、制品、验证结果和边界见 [NeoECO GTO 兼容记录](GTO_NEOECO_GTO_COMPAT_2026-10-26.md)。历史客户端结果不代表本版已经验证客户端界面或联机。

远程 UI 补丁同时支持 NeoECO 原正式包与上游 `cca7fbb` 的客户端类加载隔离实现，按实际字节码识别，不依赖未变更的 `20.5.0-gto.1` 版本号。更新范围及测试制品边界见 [远程 UI 补丁更新记录](GTO_NEOECO_UI_UPDATE_2026-10-26.md)。

当前补丁还保留 Core `2.0.3-gto.6` 已完成的客户端世界隔离，避免两项目重复转换导致专服启动失败。最新验证为 JUnit 1575 项通过、5 项跳过，以及安装 NeoECO 的 122 项 GameTest 全部通过；当前配套制品哈希、正式包加载与重启结果见 [Core gto.6 配套验证](GTO_NEOECO_CORE_GTO6_2026-10-27.md)。相同版本号的历史制品应按哈希区分。

## 适配边界

- 普通实体 CPU 委托给 GTO 原生执行器，并保留 NeoECO 安装的 CPU 包装；含 LT 超载样板的计划继续使用 LT 时轮逻辑，保留实体 CPU 容量、协处理器预算、暂停、存档和产物归属。
- GTO 26.10.10 随机化部分内部类与方法名。样板解码通过参数和返回类型识别原生调用，CPU 剩余量通过缓存访问器读取其持久化字段契约，不绑定旧公开实现类名。
- NeoECO 批量准入、任务 UUID、取消、完成和精确数量派发通过其公开 API 接入；模糊回料状态提供不要求注册表参数的 Forge 持久化入口。接收结果不确定时暂停任务并保留资源归属，避免自动重复派发。
- 样板迁移使用 NeoECO 的真实物理槽位，并检查整个工作站网络是否仍有工作线程持有任务；LT 与 NeoECO 共用每个 ME 网络的迁移锁。
- NeoECO 远程界面的客户端世界读取隔离到客户端辅助类，避免正式发行包在专用服校验共享 UI 类时加载 ClientLevel。保持原 UI 和同步协议，不修改 NeoECO 原件。
- TB 候选算法不适用时仍调用 GTO 计算任务。直接 BigMEStorage 保留 BigInteger 精度；经存储总线跨 ME 网络时沿用 GTO 去重与单次 long 数量边界。

## 构建

使用 Java 21。当前已验证 Core 制品以 [最新配套记录](GTO_NEOECO_CORE_GTO6_2026-10-27.md) 的哈希为准。以下补丁保留历史重建路径，未包含 Core 后续增加的 NeoECO 远程 UI coremod 与批量适配器，不能据此重建该记录中的同一制品。历史公共源码基线为 `7c7a066af9d404010fe0b9472fca687879b36281`，依次应用：

1. [gto.5 完整补丁](docs/patches/core-gto.5-from-upstream.patch)
2. [gto.6 增量补丁](docs/patches/core-gto.6-from-gto.5.patch)

在 Core 源码中运行：

```sh
./gradlew check jarJar verifyReleaseJar publishMavenJavaPublicationToMavenRepository
```

再构建 LT：

```sh
./gradlew -Pthunderbolt_local_repository=/absolute/path/to/core/repo check jarJar
```

Core 默认本地仓库路径仍为同级 `../Thunderbolt-Core-Reborn/repo`。LT 不再需要以 GTOCore 内部 CPU 类作为编译依赖，也不会嵌入 GTOCore 或 NeoECO。只分发 `build/libs` 的正式主 JAR，测试代理、slim、sources 和 QA 包均不是游戏依赖。

## 隔离游戏测试

```sh
./gradlew -Pthunderbolt_local_repository=/absolute/path/to/core/repo -PgtoQa gtoQaJar gtoQaLtJar
```

在隔离 GTO 实例内安装配套 TB、正式 NeoECO GTO、`build/gto-qa/gto-compat-qa.jar`，并以 `ae2lt-gto-qa-instrumented.jar` 替代 LT 主包。该包保留正式生产类并附加测试夹具。其余中间 JAR 不要放入 mods。

使用 `-Dae2lt.gtoQaSuite=neoeco|base|exact|dispatch|batch|interfaces|io|infinite|recipes|content|content-recipes|permissions|none` 选择测试组，每次新建测试世界。测试应同时满足 CPU、STORAGE、PLANNER 三个 PASS 与最终 `GTO_COMPAT_QA PASS total=... failed=0 startupFailures=0`；只出现服务端 Done 或退出码 0 不能证明通过。`neoeco` 组要求安装上表指定的正式 NeoECO。

JUnit 的正式 NeoECO API/字节码测试读取 `build/neoeco-compat/neoeco-release.jar`。放入兼容记录列出的原件并校验哈希后再运行 `check`；缺包时这三项会明确跳过。测试原件不嵌入生产制品。

以下内容保留历史追溯，涉及的旧版本、数量及兼容限制均以各自日期为准。

## 历史验证记录（gto.8 及更早，不能作为本版结果）

- `compileJava`、`compileTestJava`、`compileGtoQaJava` 及可选游戏测试/客户端探针源文件均编译通过。
- `check`：1489 个单元测试，1484 个通过、5 个跳过、0 个失败；包括 5 个精确数量切片边界测试。
- `jarJar` 与 `gtoQaJar` 完成打包和重混淆。
- 独立字节码探针验证了 AE2-GTO 15.2610.8 上的 CPU 拆除注入，以及原生终端接口和单次库存同步注入。
- 已在隔离的完整 GTO 整合包中运行专用服游戏测试和真实图形客户端；发现并修复了 GTO 不提供空白样板槽时天枢编码终端打开越界的问题。
- GTO 接管 EMI/JEI 插件发现，LT 通过 GTO 的公开插件事件注册自身分类与转移处理器。
- 专用服四组共 34 项实测通过；真实客户端的原生/天枢大数终端、EMI 交互预览与正式包独立联机均通过。两项 NeoECO 用例因未安装该附属跳过。
- 实测范围、通过数量、原始日志、截图及排查边界见 [2026-10-19 实机测试记录](GTO_REAL_TEST_2026-10-19.md)。
- 补充审计检查了 5202 处外部成员引用、1866 个具体类及真实客户端的 73 个 Mixin 目标类；关键 JEI/EMI 配方转移断言通过。条件跳过和配方输入重叠提示见 [兼容性补充审计](GTO_COMPATIBILITY_AUDIT_2026-10-19.md)。
- 后续修复组装室在原配方映射内替换内容时沿用旧对象的缓存缺陷。完整 `check` 更新为 1491 项，1486 通过、5 跳过，0 失败；新正式包的 9 项专用服配方用例通过，覆盖 7 个提示配方及两种指定混合投料。新制品 SHA-256 为 `9e90bf5ba86c0dca0a5248a87ffc8032657176b7a86095972962687d31fb23e2`，详细结果见上述补充审计。
- 全面复查又修复共享机器合成执行器的部分扣料漏退、闪电提取回调填满输出槽后可能留下免费产物两处缺陷。完整 `check` 为 1495 项，1490 通过、5 跳过，0 失败；正式包的 13 项整包用例、349 条配方网络往返、真实客户端及 73 个 Mixin 目标类检查均通过。当前正式包 SHA-256 为 `ec64cf561010d84fec7f5f70291eb0b88b40b62c1032b51b9460674426be2d29`。本轮覆盖及证据见 [全面复查记录](GTO_FULL_AUDIT_2026-10-19.md)。上述旧制品结果保留为历史记录。

2026-10-20 已移植上游 `main` 的 `7c76557f`，修复过载 IO 重读空槽快照后矩阵加成和过滤器残留。JUnit 1495 项中 1490 通过、5 跳过；隔离完整 GTO 专用服的过载 IO 40 项及 CPU、STORAGE、PLANNER 探针全部通过。该轮正式包 SHA-256 为 `45cc01e0637695eee6034965642900a65f3c15e5845b25d4ac5600d41b1ff5ac`，作为历史记录保留。移植细节、GTO 元件缓存时序和证据见 [上游修复移植记录](GTO_UPSTREAM_PORT_2026-10-20.md)。

2026-10-20 继续移植无限存储回归及配套 Core 优化，版本更新为 LT `2.1.2-gto.2` / TB `2.0.3-gto.2`。旧 Core 的通道单位变化后摘要失效问题已通过专用服对照复现，新配套版本的无限存储综合测试及 40 项过载 IO 回归全部通过，两轮的 CPU、STORAGE、PLANNER 探针均通过。LT 全检 1495 项（1490 通过、5 跳过），Core 全检 1544 项（1531 通过、13 跳过），均无失败。当前 LT 正式包 SHA-256 为 `86a56e314d060b97daf6e9c3d88096cdc1fca94f97fce4c443140880b0598168`，替代上文历史制品。配套制品、Core 独立目录与补丁及详细证据见 [无限存储移植记录](GTO_INFINITE_CELL_PORT_2026-10-20.md)。

2026-10-20 对同一配套正式制品进行新世界全面重跑：LT 1495 项（1490 通过、5 跳过）及专用服七组 88 项均无失败，磁盘存档 45,063 个唯一键逐项核对通过；正常主菜单进入世界后的 EMI 交互检查通过。正式包联机一次入服成功，但修正测试身份后的另一新世界对照运行登录超时，不能视为稳定通过。本轮 Core 完整测试仍不稳定，最后一次主测试 1505 通过、13 跳过、3 失败，后续相关 48 项定向复测通过；QuickPlay 启动曾出现 GTO 重复配方注册错误，仍未解决。因此上文移植阶段的零失败记录仅为历史证据，不能视为本轮全部通过。测试代理的 ASM 依赖打包已修复，正式游戏 JAR 未改变。详细限制、原始失败证据及测试制品哈希见 [本轮全面测试记录](GTO_COMPREHENSIVE_TEST_2026-10-20.md)。

`gto.3` 已移植上游自由视角实体判定、统一合成派发、缺料唤醒、候选索引、NeoECO 回料接口及无限盘快照维护重构。该版本的验证结果与范围见 [2026-10-20 移植记录](GTO_LATEST_UPSTREAM_PORT_2026-10-20.md)，按历史记录保留。

历史 `gto.4` 修复大数合成报告 0 字节显示、适配 Forge NeoECO 精确提交和取消接口，并增加默认开启的 Mekanism/工业先锋 AUTO 机器保护开关。该版成品与验证范围见 [2026-10-21 移植记录](GTO_UPSTREAM_PORT_2026-10-21.md)。

## 来源

- GTO 整包：[GregTech-Odyssey 0.6.0 基线](https://github.com/GregTech-Odyssey/GregTech-Odyssey/tree/3650c7d330ade1b744ade0bc303fe5111eac98fa)
- GTOCore 源码：[GTOCore-Main](https://github.com/GregTech-Odyssey/GTOCore-Main)
- AE2-GTO 源码：[Applied-Energistics-2-gto](https://github.com/GregTech-Odyssey/Applied-Energistics-2-gto)
- 迁入的 AE2 LGPL 类见各仓库 `THIRD_PARTY_NOTICES.md`。

历史 `gto.5` 按要求清除 NeoECO 专用合成、样板迁移、配方和配套 Core 适配；详情及验证见 [NeoECO 清除记录](GTO_NEOECO_REMOVAL_2026-10-21.md)。

清除前 `gto.4` 的完整检测与原始失败见 [2026-10-21 全面测试记录](GTO_COMPREHENSIVE_TEST_2026-10-21.md)。替代材料返回后的合成唤醒失败在 `gto.5` 仍可复现，后续在 `gto.8` 定位为 AE2-GTO 有效性缓存未命中误判并修复；历史失败记录保留。

`gto.6` / Core `2.0.3-gto.5` 移植终端服务端广播保护、跨补料周期公平分配、玩家方块权限与自动建造回滚、频率 OWNER 上限和密码限流。详情见 [gto.6 移植记录](GTO_UPSTREAM_FIXES_GTO6_2026-10-21.md)。当前 `gto.8` 保留上述修复及 gto.7 的 JEI 更新，并修复 AE2-GTO 替代材料缓存与 Krypton 登录阶段发送刷新。NeoECO 继续不兼容；Core 仍使用清除后的 gto.5，无需重建新 Core 版本。`permissions` QA 组检查权限事件、放置回滚和频率限制。
