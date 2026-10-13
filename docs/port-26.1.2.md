# Minecraft 26.1.2

验证环境：Java 25、NeoForge 26.1.2.109、AE2 26.1.12-beta。此分支保留原有架构，仅适配不兼容的构建、存储、资源、渲染及模组 API。

## 构建

将三个仓库的 `26.1.2` 分支放在同级目录，目录名分别为 `Thunderbolt-Core-Reborn`、`AE2-Lightning-Tech-Reborn`、`AE2LT-Packaged-Pattern-Provider-Reborn`，按此顺序执行：

```sh
bash ./gradlew check jar
```

使用 Java 25。首次构建需要联网解析依赖；依赖已缓存后可加 `--offline`。LT 默认读取相邻 Thunderbolt 的构建 JAR，PP 默认读取相邻 LT 和 Thunderbolt 的构建 JAR；自定义位置可用项目 Gradle 属性覆盖。构建结果在各项目的 `build/libs`。运行所需的第三方模组不随这三个 JAR 一起分发。

## 适配与验证

基线 main：`5e12a0d7266c7dc68fdce9b628ec77af09750e3c`。附加运行依赖采用 AE2WTLib 26.1.1-beta、Curios 15.0.0+26.1.2、GuideME 26.1.10-alpha。

- 适配注册、ValueIO/NBT、配方模板与配料表示、能力事务、菜单、网络和 26.1 客户端渲染。
- 更新 AE2/AdvancedAE CPU 存档、玩家输入与地形渲染 Mixin，恢复 Jade 和 AE2 内置 JEI 转移接口。
- 保留原有机器、规划器、CPU 执行和批次结构。

2026-09-23 验证结果：

| 验证 | 结果 |
| --- | --- |
| `bash ./gradlew check jar` | 1197 项通过 |
| `bash ./gradlew -Pae2ltPureMixinMode=true pureMixinTests` | 21 项通过 |
| `bash ./gradlew -Pae2ltCpuSelectionGameTest runCpuSelectionGameTestServer` | 25 项必需测试通过 |
| 联合专用服务器、实际图形客户端 | 通过下述范围 |

GameTest 包括 CPU 选择、缓存失效、大整数订单、Pigmee 机器、AE2/AdvancedAE 带 37 件待收产物的存档往返、组件及数量保留、分批认领和防止重复认领；还验证了 AdvancedAE 方向样板、ExtendedAE 原生配方数据，以及 Applied Flux 真实 FE 元件经 AE 网络向远端工厂送电和能量守恒。

客户端包括有线/无线终端的 `10^100 + 12345` 数量输入、确认、提交、状态、取消和精确退款；电磁炮主副手姿势、七类自定义配方同步、JEI 配方页、多方块预览、Pigmee 与天枢 JEI 填充、PP 菜单及自动返还开关。IPN 2.3.7/libIPN 6.8.3 实际运行时确认天枢屏幕及无线菜单屏蔽整理。测试模组与探针不进入发布 JAR。

## 调度性能验收

保留原有验收断言。2026-10-13 同步 alpha 后，两项无线轮询性能失败仍存在，两项自适应批次成本已达到原门槛；不能据此声称全部测试通过。

| 用例 | 实际结果 | 原门槛 |
| --- | --- | --- |
| FAST 冷产物第 5 相位 | 等待 19 tick | ≤6 tick |
| 256 目标转换连续阻塞 | 12 次 | ≤5 次 |
| 自适应批次重置恢复 | D 派发成本 340%（原 660%） | ≤400% |
| 自适应批次重配置 | RC4 峰值 351.56%（原 742.19%） | ≤400% |

`-Pae2ltWirelessPortGameTest runWirelessPortGameTestServer`：44 项无线用例中 42 项通过、2 项失败。`-Pae2ltAdaptiveBatchMode=true test`：6 项全部通过。

## 可选联动与限制

- 已验证上述范围：JEI 29.40.0.102、Jade 26.1.11、AdvancedAE 26.1.7、ExtendedAE 26.1-1.0.3、Applied Flux 26.1-1.0.1、IPN 2.3.7。
- EMI 本次暂不适配，保留源码并排除相应客户端集成。
- 核查日期 2026-09-23：Veil、Mekanism、Flux Networks、Polymorphic Energistics 未取得可验证的 NeoForge 26.1.2 版本。NeoEcoAE 旧版批次适配器与 CPU Mixin 暂不打包。保留的旧 `compileOnly` API 不代表对应运行时已验证。
- ExtendedAE 完整机器生产周期及 Jade 每个提示字段未逐项测试；CPU 测试覆盖序列化及认领，不覆盖异常断电恢复。
- 联合客户端中 MA、ExtendedAE、Applied Flux 自身创造栏重复物品导致 JEI 跳过对应栏的收集；Re:Avaritia 自带 `ae2_creative_energy_cell` 仍有旧配料格式错误。这些第三方问题未在本项目修补。
- ExtendedAE 的 `overload_processor` 配方保留模组加载条件，但移除了未安装 ExtendedAE 时无法解析的旧 `extendedae:config` 条件；关闭 ExtendedAE 对应配方开关不会再关闭 LT 此配方。

## alpha 最终状态同步（2026-10-13）

以 alpha `21926da0..2afebd04` 的最终差异为依据，同步公共扩展 API、客户端与逻辑包划分、无线传输与能量访问、机器输入转移、矩阵样板迁移、天枢设置与补货、闭环种子借用、强制合成与缺料书签、装备及显示资源。保留 26.1.2 的事务、能量预留、ValueIO、配方模板与渲染接口。AdvancedAE 反应配方使用原始 ID 的执行视图，处理器配方按最终加载的压印器数据派生。

水晶科技 `crystal_catalyzer/ae2cs/` 配方未同步。EMI、UselessMod、Mob Grinding Utils 未取得对应的 26.1.2 发布版，相关新增兼容跳过；旧 NeoEco CPU/批次适配器继续排除，26.1 源码的晶体物品仅通过原生注册条件引用。

已核对的远端源码快照：

| 项目 | 分支 | 提交 |
| --- | --- | --- |
| AE2 | `26.1` | `4ba637f531d9` |
| AdvancedAE | `main` | `5ad43ee1e5f7` |
| ExtendedAE | `26.1.2-neoforge` | `1fb32a682956` |
| ExtendedAE Plus | `26.1.2` | `81815d0af1a3` |
| Applied Flux | `appflux/26.1.2-neoforge` | `24cad7759bef` |
| AE2WTLib | `26.1` | `ac9e3cd8c0db` |
| JEI | `26.1` | `3f982442d1b3` |
| Curios | `26.1.2` | `8f2f1321e4c3` |
| Silent Gear | `26.1.x` | `cb9926f4a321` |
| Applied Generators | `main` | `f1b746c7d85c` |
| NeoEcoAE | `26.1` | `f95af5c1933c` |
| IPN | `all-in-one` | `bb3acc6f6ccb` |

Applied Flux 输出适配 `UniversalEnergyHandler.send`，Silent Gear 破损判断适配 `ItemInstance`，新增配方存在性条件使用 `neoforge:registered`。JEI 使用 29.47.0.109；EAEP 1.6.3 的真实 JAR 合约测试可用 `-Pae2ltEaepTestJar=<jar>` 启用。隔离类加载用例随 `-Pae2ltPureMixinMode=true pureMixinTests` 执行，避免 FML 的保护占位类改变测试对象。

本次验证：

| 验证 | 结果 |
| --- | --- |
| `check jar -Pae2ltEaepTestJar=<1.6.3 jar>` | 1488 项全部通过；未提供外部 JAR 时其中 4 项默认跳过 |
| `-Pae2ltPureMixinMode=true pureMixinTests` | 26 项全部通过 |
| EAEP 1.6.3 实际 JAR 合约 | 4 项全部通过 |
| 安装 EAEP 1.6.3 后的联合服务器 | 25 项 CPU/联动必需用例全部通过；EAEP 附带的 `advanced_ae:oblivion_singularity` 使用未注册的旧 `advanced_ae:reaction` serializer，未加载该第三方配方 |
| `-Pae2ltCpuSelectionGameTest runCpuSelectionGameTestServer` | 25 项必需用例全部通过 |
| `-Pae2ltWirelessPortGameTest runWirelessPortGameTestServer` | 44 项：42 通过；原冷产物 19 tick 与连续阻塞 12 次两项性能失败保留 |
| `-Pae2ltAdaptiveBatchMode=true test` | 6 项全部通过，保持原吞吐和派发成本门槛 |
| `-Pae2ltFumoRenderClientProbe runFumoRenderClient` | 实际客户端通过：32 个放置状态、16 个物品场景、96 个半砖状态、双闪电等级提示和世界图标、彩虹 GPU shader 与 GUI 空槽图标 |
