# GTO 1.20.1 兼容构建

本分支基于 Reborn `1.20.1`，用于 GTO 整合包，不是普通 AE2 版本。

## 配套版本

| 组件 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Java | 21 |
| Forge | 47.4.20 |
| GTO 整包基线 | 0.6.0-dev15，公开 0.6.0 包提交 `3650c7d330ade1b744ade0bc303fe5111eac98fa` |
| GTOCore | 26.10.5 |
| AE2-GTO | 15.2610.8（使用 GTOCore 26.10.5 原包内嵌版本） |
| Thunderbolt | 2.0.3-gto.5 |
| AE2LT | 2.1.2-gto.8 |

当前替代材料与联机修复见 [gto.8 修复记录](GTO_RUNTIME_FIXES_GTO8_2026-10-22.md)。同版本更新、测试结果与配套成品见 [gto.7 移植记录](GTO_FORGE_UPDATE_GTO7_2026-10-22.md)。此前终端、派发公平性与权限修复见 [gto.6 移植记录](GTO_UPSTREAM_FIXES_GTO6_2026-10-21.md)。

客户端与服务端一起替换对应的 TB/LT JAR，移除同 mod ID 的旧版本。GTOCore、GTOLib 与 GTO 自带 AE2 均使用原件；无需修改 GTO。不要把这两个包放进普通 AE2 整合包，也不要把普通 LTPP/其他依赖旧 AE2 API 的附属兼容性视为本次验证范围。

## 同步 1.20.1 分支

当前工作树已对照 `1.20.1` 分支至 `c568e675`，移植适用更新并保留 GTO 等效实现；NeoECO 专用提交和路径继续排除：

- 接口导出调度与供应器派发、回收快照优化。
- 普通和批量供应器混合执行精确订单，保留真实投料、返回、取消和存档的数量归属。
- 原生终端菜单的大数库存同步与显示，以及天枢多方块布局、顶部端口和 JEI 预览调整。
- EMI 运行兼容下限放宽至 `1.1.22+1.20.1`。
- 最终合成派发、物理 long 边界、精确回料、无限盘摘要、IO 重读和自由视角隔离。
- Mekanism / Industrial Foregoing 机器回料适配。
- JEI 旧/新转移服务、按钮控制器分流，以及旧/新书签 API 测试兼容。

GTO 26.10.5 整包验证使用自带 JEI `15.59.0.212`。其他 JEI 版本的接口测试不能代替整包运行验证；GTO 自身的 JEI Mixin 也会限制可替换版本，详见本轮报告。

保留上表中的 Forge、AE2-GTO 和 Thunderbolt 版本，不切换为普通 AE2 或 Thunderbolt beta3。
缺少的 `BigLongSlice` 移植到 LT 自有兼容包，并沿用 GTO Thunderbolt 的三参数精确报告 API。
终端数量显示继续使用 GTO 的 `IRepoSlot` 渲染入口；实体 CPU 拆除时同时回收普通库存和产物缓冲。
当前网络协议为 `13`（含供应器机器适配开关同步），客户端与服务端必须一起更新。

## 不支持混用最新 AE2 源码依赖

公开 GTOCore 26.10.5 原件的 `CreativeCellInventoryMixin` 仍要求 `configured: java.util.Set`。外置 AE2-GTO 15.2610.10 已将其改为 `AEKeySet`，该组合会在 GTO 自身的客户端 Mixin 中失败。因此此构建的编译依赖与运行版本均锁定 15.2610.8；不要单独覆盖 GTO 自带 AE2。本次没有修改 GTO 来绕过这个错误。

## 适配边界

- TB 在 GTO 的合成提交入口捕获库存快照，在其已有工作线程上执行候选算法。候选算法不适用时，调用原有 GTO 计算任务并保留其返回对象及元数据。取消、候选失败和 CRAFT_LESS 继续经过 TB 的有界执行器。
- GTO 已删除的 AE2 模拟库存类迁入 TB 自己的包，保留 LGPL 声明，不向 `appeng` 包补回旧类。
- 普通实体 CPU 任务使用 GTO 原生优化实现。含 LT 超载样板的任务使用 LT 自有调度器，并保留实体 CPU 的容量、协处理器预算、暂停、存档、CraftingLink 身份与产物核算。通过 AE2 CPU 集群安装 GTO 公共 CPU 类的子类，不注入或改写 GTO 类。
- LT 的 JEI 处理器迁入自有包；更新 GTO 的 GenericStack、菜单、数量显示、FastCollection 与 CraftingPlan API。
- 直接挂载的 BigMEStorage 保持 BigInteger 精度。经存储总线等连接其他 ME 网络时，使用 GTO 原生去重查询；这条跨网络路径的单次操作和可见数量受 `Long.MAX_VALUE` 限制。不能把多个指向同一库存的连接当作多份库存。
- AE2-GTO 原生物品键忽略仅 ForgeCaps 不同的身份差异；此构建沿用该语义，不承诺恢复普通 AE2 的 capability-only 区分。

## 构建

使用 Java 21。当前配套 Core 的独立源码、基线与完整补丁见 [NeoECO 清除记录](GTO_NEOECO_REMOVAL_2026-10-21.md)，需使用配套 Core `2.0.3-gto.5`。先构建 TB 并发布到其工作树本地 Maven 仓库：

```sh
./gradlew check jarJar verifyReleaseJar publishMavenJavaPublicationToMavenRepository
```

再在 LT 仓库构建：

```sh
./gradlew check jarJar
```

LT 默认从同级 `../Thunderbolt-Core-Reborn/repo` 查找配套 TB。目录不同时传入 `-Pthunderbolt_local_repository=/绝对路径/repo`。

LT 编译时下载公开 GTOCore 26.10.5 原件，并校验 SHA-256 `8e6260ac596091ca876506e70b7715249b7198ddbd5d833059c3609ac751e465`。也可传入 `-Pgto_core_jar=/绝对路径/gtocore-forge-1.20.1-26.10.5.jar`，仍执行相同校验。此依赖只用于编译，绝不嵌入 LT。

分发 `build/libs` 中没有 `-slim`、`-sources`、`-gto-test-agent` 后缀的主 JAR。单元测试用 javaagent 仅将 AE2-GTO 的基础身份 mixin 应用于没有 ModLauncher 的 JUnit JVM，不是生产依赖。

## 实机验证复现

LT 保存了可选测试源集 `src/gtoQa`：

```sh
./gradlew -PgtoQa gtoQaJar gtoQaLtJar
```

在**隔离测试用** GTO 服务端/客户端放入配套 TB 主 JAR、`build/gto-qa/gto-compat-qa.jar` 和 `build/gto-qa/ae2lt-gto-qa-instrumented.jar`。后者替代 LT 主 JAR，保留所有生产类字节并附加测试夹具，避免 Java 21 的模块分包冲突。`gto-qa-fixtures.jar` 和 `gto-qa-main-dev.jar` 均为构建中间件，不能单独放入 mods。正式游戏使用 `build/libs` 的 LT 主 JAR。

测试模组会在服务端启动后放置结构并自动运行用例；用 `-Dae2lt.gtoQaSuite=base|exact|dispatch|useless|interfaces|batch|io|infinite|recipes|content|content-recipes|permissions|none` 选择测试组，每次使用独立的新存档。`recipes`、`content` 及两者合并的 `content-recipes` 可直接配合正式 LT 主包使用；其余组中的包内夹具需要上述 instrumented 包。`io` 执行 40 项过载 IO 测试，包括矩阵空槽重载和四种服务器菜单操作。`infinite` 执行八元件无限存储综合回归，并注册测试通道检查单位变化后的摘要重算。测试夹具为超出模板的多方块保持区块加载，并打开 GTO 的玩家加入后合成更新条件；精确配方测试使用自有测试配方，不改 GTO 缓存的配方表。不要在正常游戏存档中运行。

客户端使用 `-Dae2lt.gtoQaSuite=none`，进入测试世界后默认检查界面与数量格式、保存截图并自动退出。附加 `-Dae2lt.bigTerminalProbe=true` 验证原生/天枢终端的大数库存与提示；`-Dae2lt.emiMultiblockProbe=true` 验证 GTO 默认 EMI 预览；原生 JEI 环境可用 `-Dae2lt.multiblockLayoutProbe=true`。GTO 的 EMI/JEMI 会接管 JEI 界面，不能把 JEMI 界面强转为原生 JEI。

服务端需出现 CPU、STORAGE、PLANNER 三个 PASS，以及 `GTO_COMPAT_QA PASS total=11 failed=0 startupFailures=0`；客户端需出现 `GTO_CLIENT_QA PASS`。测试覆盖真实 Mixin 后的计算入口、原生回退、共享库存去重、大数库存、挂载顺序、实体 CPU 超载投料/暂停/存档恢复/变体返回/普通任务切换、满库存下运行中及空闲 CPU 拆除时普通库存与产物缓冲的完整回收和重复调用防复制，以及 8 个时轮状态和共享种子循环测试。它不等于整个整合包所有配方和所有可选附属的穷举测试。

## 本轮验证记录

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
