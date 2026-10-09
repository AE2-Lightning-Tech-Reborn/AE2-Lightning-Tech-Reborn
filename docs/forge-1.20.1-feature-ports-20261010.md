# Forge 1.20.1 / GTL 主线与功能分支移植

生产基线是 Forge 47.1.47、Java 17、AE2 15.4.10 和 Thunderbolt Reborn GTL 2.0.0。原用户工作区保持独立，移植与验证在隔离 worktree 完成。

| 上游快照 | 对应 GTL 分支 |
| --- | --- |
| main `2d8369e1` | `main-1.20.1_GTL` |
| feat/large-overload-factory `ee16ce3d` | `feat/large-overload-factory-1.20.1_GTL` |
| feat/celestweave-armor-art `fee0ad22` | `feat/celestweave-armor-art-1.20.1_GTL` |
| feat/cs-overload-parallel-20261002 `36a3f089` | `feat/cs-overload-parallel-20261002-1.20.1_GTL` |

此主线提交包含天枢有线/无线合成终端、五类真实工作站、过载合金砧、合成报告与缺料收藏/重新计算、EAEP 强制下单等待、增强无线终端和 Useless 寰宇样板编辑边界。三个独立功能分支由主线派生，其具体移植和验证记录随各分支提交。

NBT 持有真实输入，结果槽仅为原版配方预览。Forge 原生 SmithingMenu、AnvilMenu、StonecutterMenu 和 CellWorkbenchBlockEntity 承担消耗、回调、XP 和配置规则。多玩家消费同步失效其他视图；终端关闭不返还输入。无线 WUT 合并预览不修改输入，拒绝同时占用的库存、升级/能源溢出及冲突状态。无线供料扫描全部背包和 Curios 终端，逐次检查链接、范围、能源和菜单身份。

网络使用 Forge SimpleChannel，主线协议为 `gtl-11`；保留原消息顺序并在末尾追加。物品数据组件使用 1.20.1 NBT。可选 AE2WTLib 和 Useless 通过缺失判断和反射边界隔离，避免服务器在未安装库时链接客户端或外部类。AE2 15 的库存快照使用 IStorageService.getCachedInventory。

GTL 的独占规划器、节点缺失 requester、Transfinite/Thunderbolt 接口、闭环种子归属、同一物理 tick 去重和接口回入缓存均保留。完成后的独立 CPU 产物若无法存入 ME，则由 CPU 保留并显示，存储恢复后再次送入。

1.20.1 ExtendedAE 没有上游 1.21 的 crystal_assembler serializer 或 config condition，故未注册该专属配方；本地原有过载处理器配方保留。Forge AdvancedAE 1.3.6 没有上游专属 EMI 类，本地 EMI 过载加工目录已经提供工厂和借用反应配方。JEI 使用其真实原生类别。

客户端穿戴效果、互动界面和 Useless 实装模组运行不能由服务器测试代替；此记录不把编译通过描述为这些路径的完整运行验证。测试日志和临时适配脚本位于忽略的 build/feature-port-20261009，不进入生产 JAR。

主线最终运行验证：工作站 65/65、兼容服务器 80/80、导入/导出行为与压力场景 77/77、能源输出专项 7/7。能源专项只注册了 7 项，不将旧基线测试数量计入本轮。最终 JUnit 1463/1463，adaptiveBatchStress 6/6，build/reobfJarJar 成功。JAR SHA-256：`9ae4edea083cc843ceda0d800ac624d864860bedc67482a1518b36f2e694629a`。

## 大型工厂 Forge 本轮验证（2026-10-10）

大型工厂来自上游 `ee16ce3d`，目标分支 `feat/large-overload-factory-1.20.1_GTL`。使用 Forge DeferredRegister、可失效的 item/energy/grid-node capability、Java 17、SavedData、NBT、AE2 15 样板编码和 Forge SimpleChannel；网络协议为 `gtl-12-large`。144 槽分页在服务器端约束 Shift 点击与拖拽目标。保留 GTL 独占规划和 Thunderbolt Reborn GTL 2.0.0 依赖。

真实服务器 GameTests：未安装 Crystal Science 37/37，安装 Forge AE2CS 1.20.1 1.2.0 37/37。每轮含 33 项默认行为、1 项配方重载、1 项重复调用压力、1 项原生调度计时、1 项真实时间轮 CPU 计时。运行入口每次创建新的测试世界，防止之前保存的工厂参与本轮全局工作额度分配。测试配方输出遵循 1.20.1 ItemStack 的 `Count` 格式。

实装目录逐项核对：Crystal Science 聚合 62/62、粉碎 24/24、蚀刻 7/7；LT 模拟室 15/15、装配室 54/54、催化器 10/10、苍穹转换 5/5（包含测试配方），同时核对原生能源换算。AdvancedAE 反应目录及真实 CPU 链亦在服务器验证中覆盖。NeoECO 未安装，反射路径未实测；ExtendedAE 1.20.1 没有水晶装配配方 API，明确拒绝该路径，工艺核心配方受 item-exists 条件保护。客户端界面、护甲穿戴与资源重载视觉效果仍未实测。

下面取安装 Crystal Science 的本轮原始结果。重复调用场景使用 16 台工厂、2304 个样板、32768 个网络键；预热 5 tick，采样 32 tick，每台每 tick 重复调用 64 次。增量耗时只描述该测试驱动阶段，不代表整服 MSPT 或任意第三方库存的耗时。

| 场景 | 平均增量 ms | P95 ms | 最大 ms | 全量库存枚举 |
| --- | ---: | ---: | ---: | ---: |
| 缺料 | 0.385 | 0.774 | 1.161 | 0 |
| 输出堵塞 | 0.072 | 0.137 | 0.597 | 0 |
| 连续完成 | 0.576 | 0.892 | 1.180 | 0 |

真实时间轮 CPU 主动场景预热 96 tick、采样 64 tick，各档 512 个完整工厂 tick 样本，16 台中 T1 与 T4 各 8 台。表中时间为单台工厂入口及回调总时间，不含共享服务与 CPU 规划时间。

| 场景 | 档位 | 平均 μs | P95 μs | 最大 μs |
| --- | --- | ---: | ---: | ---: |
| 每 tick 一批 | T1 | 32.79 | 59.3 | 339.9 |
| 每 tick 一批 | T4_unlimited | 23.90 | 37.6 | 157.6 |
| 两段同 tick 链 | T1 | 66.16 | 62.5 | 6254.2 |
| 两段同 tick 链 | T4_unlimited | 27.87 | 42.0 | 1439.7 |
| 每 tick 32 个小任务 | T1 | 86.23 | 126.8 | 403.3 |
| 每 tick 32 个小任务 | T4_unlimited | 85.04 | 136.6 | 659.0 |

原生调度 16 组场景最大单台样本为 6699.8 μs，所有压力、主动和原生场景全量枚举均为 0。初始化、JVM 编译、GC 及系统调度仍可能产生峰值，未达到任何情况下单台低于 50 μs 的保证。本地产物目录的 `validation/` 保存两轮日志以及完整 JSON（含 P99、超线次数、阶段峰值）。

复现：`./gradlew -I scripts/large-factory-test.init.gradle -x downloadAssets runInterfaceIoGameTestServer`；实装时增加 `-Pae2ltCrystalScienceJar=<Forge AE2CS jar 路径>`。测试入口默认创建唯一目录；如指定 `-Pae2ltLargeFactoryRunId=<标识>`，请为每轮使用新标识。

大型工厂发布验证：安装与未安装 Crystal Science 两轮 GameTests 均为 37/37；完整计时与压力数据、版本限制及复现入口见上节。
该分支最终 JUnit 1518/1518，adaptiveBatchStress 6/6，build/reobfJarJar 成功。JAR SHA-256：`86852879b13e6492491e9817a9ea69a7f0c5c722467f308d505d60d006574840`。
