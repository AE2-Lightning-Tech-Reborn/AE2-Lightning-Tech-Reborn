# CTNH Core 同装适配

适配对象为 Minecraft 1.20.1 / Forge 47.1.47 / Java 17、官方 AE2 15.4.10，以及 CTNH Core 1.4.3a（CurseForge 8009041，内嵌 Energy 0.5.4）。不替换 AE2，也不修改或重新打包 CTNH/PCC 发布 JAR。

本文的 AE2LT 为本 PR 的本地构建，版本字段仍为 2.1.4；已发布的正式 2.1.4 不含本次补丁。

固定来源：[Core 96f57ac](https://github.com/CTNH-Team/CTNH-Core/tree/96f57ac211c5ed5c917d6486987b4402ade984b1)、[Energy c4be323](https://github.com/CTNH-Team/CTNH-Energy/tree/c4be3231fad247511efb28737ee497ec91bbe9b1)、[参考包 ca5f508](https://github.com/CTNH-Team/Create-New-Horizon/tree/ca5f508)。Core 的 SHA-256 为 `91c6a1dfa84bee15078b41787b85f7f0327880138832bfe20b7bccc962220804`。Energy 对应关系来自前序逐文件核验；其余精确随包源码未锁定的模块按 JAR 元数据/字节码评估，不用 dev 源码代替。

## 已处理的接口差异

- 超载样板供应器直接返回自己重建的样板目录，避免继承 Energy 的另一份目录。
- 检测到 Energy 时，为原版样板编码终端补充缺失的 `enableCircuit` 布局；已有布局保持不变。
- PCC 1.2.3 入口仅接受 `FMLJavaModLoadingContext`。Forge CoreMod 在该类缺少无参构造器、且具有预期上下文构造器时，补充一个委托入口，保留原有初始化。
- PCC 类内的两参数 `FMLJavaModLoadingContext.registerConfig` 调用转接到活动模组的 `ModLoadingContext.registerConfig`，保留客户端和通用配置注册。
- 旧 Forge 缺少 CTNH Bio、Energy 等调用的 `ResourceLocation.parse(String)`、`fromNamespaceAndPath(String,String)`。仅在方法缺失且对应构造器存在时增加委托入口，继续使用原版构造器进行标识符校验；已有方法不覆盖。
- Re-Avaritia 1.3.9.3-alpha4 的 `SingularityItem.<clinit>` 使用非守护的 `Singularity Icon Timer`，专服保存停止后仍保活 JVM。仅将该命名计时器的 `Timer(String)` 构造改为 `Timer(String, true)`，保留定时任务及运行周期；其他计时器和已有守护线程入口不改动。

CoreMod 声明位于 `META-INF/coremods.json`，脚本位于 `coremods/`。PCC 适配只转换 `yuuki1293.pccard.PCCard`；资源标识符适配只扩展上述两个静态入口，不改写现有解析方法或构造器；计时器适配只转换 Re-Avaritia 的奇点物品类。脚本不在磁盘上改写前置或 CTNH JAR。

## 同装条件

这不是向原环境只添加一个 CTNH JAR 即可使用的组合。需要同时满足发布包外部依赖及其实际 API 要求，并为 Core 的高阶配方启用 GT 高阶内容。

| 范围 | 判定 |
| --- | --- |
| 仅将完整 Core JAR 放进现有最小基线 | 必需前置缺席会加载阻塞，不能原样同装。 |
| Forge 47.1.47 配参考包 FTB Library 2001.2.12 | 声明版本硬阻塞；不能仅依靠本补丁解除。 |
| 本补丁、兼容前置及高阶配置齐备的隔离组合 | 有条件同装；已执行范围通过，仍有未覆盖功能。 |
| 用户现用客户端/专服、PP 与已有 GT 的选择结果 | 证据不足，未获得对应实例清单及哈希。 |

- `config/gtceu.yaml` 的 `machines.highTierContent` 必须为 `true`。Core 1.4.3a 的 `MachinesRecipes.init` 第 275 行无条件引用 `GTItems.CONVEYOR_MODULE_UHV`；嵌入 GT 7.5.0-ctnh 在开关关闭时将其置为 `null`。CTNH 参考包 `CTNH-Team/Create-New-Horizon@ca5f508` 的对应配置为 `true`。AE2LT 不自动改写该配置或省略配方。
- 声明的前置包括 Ad Astra、LDLib、Configuration、Create、Biomancy 2.8.19.0、Jade、Hostile Neural Networks、Create: Metallurgy、KubeJS、Botania，以及它们各自的库。
- 已验证的 AE2 相关前置组合包括 ExtendedAE 1.20-1.4.10-forge、AE2 Crystal Science 1.1.4.514、PCC 1.2.3、AE2WTLib 15.3.3-forge、AE2OmniCells 1.20.1-forge-1.1.0。它们的版本不代表用户现装实例已核实。
- Core/内嵌模块还直接引用 GregTech Construct 提供的 Tinkers API、Mantle、TerraBlender、Apothic Attributes、Legendary Survival Overhaul、Sophisticated Core、Aether、Deep Aether、Re-Avaritia、JEI、Create New Age、EnderIO、Twilight Forest、Blood Magic、ExtraBotany、EventWrapper、MythicBotany/LibX、Ars Nouveau、Apotheosis、Cataclysm；需按引用版本递归补齐前置。Core 生物群系 JSON 还使用 Alex's Caves 2.0.2 的生物分类，即使创建平坦世界也需要该前置；`TagLoaderMixin` 的字段解析需要 FTB Ultimine 及其 FTB Library。它们不是 AE2LT 新增的强制依赖。
- 参考包的 FTB Library 2001.2.12 声明 Forge `[47.3,)`，是 Forge 47.1.47 的硬阻塞，不能照搬。隔离兼容组合使用 FTB Ultimine 2001.1.7 和较早的正式 FTB Library 2001.2.5（其 Forge 范围为 `[47.0.1,)`，满足 Ultimine 的 Library `[2001.2.1,)`）；2.5 的发布引用及 SHA-1 来自参考包历史提交 `866925c`，没有改写版本约束。

完整 Core 保留其 8 个直接内嵌 JAR：GT 7.5.0-ctnh、CTPP 0.0.14、Bio 1.0.0、Mana 0.7.6.1、KAllFix 1.0、StarCarefree 1.1.1（`mods.toml` 版本，清单写 1.0）、Energy 0.5.4、Lib 0.1.1。已有 `gtceu` 时还必须核对实际 JarJar 选择和 ABI；不得据此直接认定两个 GT 发布包可互换。

## AE2 改动与归属

以下汇总前序静态审查的主要交集；Mixin 名称对应随包配置中的类。`ae2.*` 的 Energy Mixin 均位于 `tech.luckyblock.mcmod.ctnhenergy.mixin` 下，KAllFix Mixin 位于 `n1luik.KAllFix.mixin.mixinfix.ae` 下。

| 归属与版本 | 文件或目标 | 影响及条件 |
| --- | --- | --- |
| Energy 0.5.4 | `ae2.cpu.CraftingServiceMixin`、`CraftingCpuLogicMixin`、`CraftingCPUMenuMixin` | 增加量子 CPU 的登记、选择、合成调度、倍增和状态展示；与 Thunderbolt/AE2LT 的合成逻辑共同变换，必须另验实际派发和取消。 |
| Energy 0.5.4 | `ae2.patternprovider.PatternProviderLogicMixin`，特别是 `getAvailablePatterns()` | 维护自己的样板表并改动推送行为；AE2LT 超载供应器通过自身 getter 返回自己重建的目录。 |
| Energy 0.5.4 | `ae2.patternprovider.PatternProviderMenuMixin`、`PatternProviderTargetCacheMixin` | 改动供应器设置及目标发现；需验证有线/无线、阻塞、回传和自动合成。 |
| Energy 0.5.4 | `ae2.patternencodingpanel.ProcessingEncodingPanelMixin`、`StyleManagerMixin` | 编码终端新增 `enableCircuit` 控件依赖；AE2LT 仅在缺失时补布局，实际客户端编码仍需验收。 |
| Energy 0.5.4 | `ae2.energy.DriveBlockEntityMixin`、`ChestBlockEntityMixin`、`MEInventoryHandlerMixin`、`StorageBusPartMixin` | 将能量相关存储接入 AE2；基础物品存取不能替代能量存储和总线的完整验证。 |
| Energy 0.5.4 | `ae2.energy.InterfaceEnergyDistributorLogic`、`PatternProviderEnergyDistributorLogic` | 介入接口和供应器能量分配；需要与 AE2LT 的能量接口/分发实际对照。 |
| Energy 0.5.4 | `gtm.MEPatternBufferPartMachineMixin`、`MEPatternBufferProxyPartMachineMixin` | 旧 GT ME 样板仓及代理加载时可能换块，必须使用存档副本验证。 |
| KAllFix 1.0 | `NetworkStorageMixin` 的 `extract`、挂载/卸载与队列；`KeyCounterMixin` | 介入聚合存储及数量处理；需验证模拟、实际提取、优先级和卸载过程。 |
| 外部 AE2CS 1.1.4.514 | `GridNode.getMaxChannels()` | 与 Thunderbolt 2.0.5 都有默认优先级、可取消 HEAD；已知宿主分支不重叠，但广播器限额进入 Thunderbolt 容量计算。此项不属于 Core/Energy 自身频道注入。 |

前序审查未发现 AE2 类替换或定向 AT；以上是同装交互，不要求替换官方 AE2。整合包 KubeJS 脚本、资源布局及配方配置不能视为 Core JAR 自动提供的行为。

## 依赖与验证边界

CTNH Core 是带有多项内嵌模块的整合包核心。适配不能代替它及其模块所需的外部前置；仅补 `mods.toml` 中声明的依赖也可能漏掉直接类引用。客户端、专服必须按实际使用的 CTNH 发布包核对完整依赖与版本。

`CtnhCoremodCompatibilityTest` 的 12 条用例通过 Forge 使用的受限 Nashorn 引擎执行实际脚本，并加载转换后的类，验证构造和配置注册、标识符校验、计时器仍执行且为守护线程、重复转换、原有入口及不支持接口的保留。

`OverloadedPatternCatalogGameTests` 在真实世界对象上覆盖普通超载、扩展超载的末尾槽位和原版供应器的样板加入、替换、移除。默认开发 GameTest 不含 CTNH，不能单独证明 Energy 组合下的行为。

当前常规 JUnit 共 1,831 条，1,815 条通过、16 条因可选正式制品条件不满足而跳过，0 失败/错误；自适应批量压力测试 6 条通过；构建、重混淆、JarJar 打包和默认 GameTest 59 条通过。跳过项为 11 条可选 API 合约、1 条 NeoEco 正式制品检查、4 条 EAEP 正式 JAR 检查。

使用上述版本及条件的隔离正式映射专服，基线、仅前置、完整 Core 三组都通过冷启动、基础运行探针、正常保存退出和同一世界重载。探针检查三类供应器目录、三台供应器基础频道活跃、物理 CPU 形成、模拟存储不变以及 37 个物品在 JVM 重启后分两次取出 11 与 26、最终归零。完整 Core 组合的实际 `CraftingService`/`CraftingCpuLogic` 已变换；这不等于量子 CPU 或所有合成分支已执行。

额外在基线与完整 Core 组合执行真实自动合成：超载供应器经物理 CPU 和分子装配室消费 4 个原木、产出恰好 16 个木板，完成后继续检查 20 tick 未出现重复产出；正常退出 JVM 后确认 16 个木板持久化，再清空并重复同一合成。两组均两次通过并正常退出。运行探针是临时独立模组，不进入发布包。

测试日志仍包含第三方语言生成/专服类扫描、可选内容的数据加载等告警与错误级日志；启动及探针通过不表示这些整合包内容已全部完整加载。

实际实例清单、双端 JAR 哈希、PP 是否启用仍未核实。客户端布局/菜单、专服登录、多玩家、AE2CS 频道边界、量子 CPU 实际派发/倍增、能量存储、撤销退款和长时间运行均需另行验证；启动通过不等于这些功能已兼容。
