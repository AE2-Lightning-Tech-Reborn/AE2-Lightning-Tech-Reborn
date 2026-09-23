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

## 保留的原有性能失败

本次明确保留 main 的轮询/调度行为及原断言，以下四项不是本分支待修范围；不能据此声称全部测试通过。

| 用例 | 实际结果 | 原门槛 |
| --- | --- | --- |
| FAST 冷产物第 5 相位 | 等待 19 tick | ≤6 tick |
| 256 目标转换连续阻塞 | 12 次 | ≤5 次 |
| 自适应批次重置恢复 | D 派发成本 660% | ≤400% |
| 自适应批次重配置 | RC4 峰值 742.19% | ≤400% |

`bash ./gradlew -Pae2ltWirelessPortGameTest runWirelessPortGameTestServer`：原 43 项无线用例中 41 项通过、2 项失败。`bash ./gradlew adaptiveBatchStress`：6 项中 4 项通过、2 项失败。原 main 注释和测试文档已记录这些性能门槛。

## 可选联动与限制

- 已验证上述范围：JEI 29.40.0.102、Jade 26.1.11、AdvancedAE 26.1.7、ExtendedAE 26.1-1.0.3、Applied Flux 26.1-1.0.1、IPN 2.3.7。
- EMI 本次暂不适配，保留源码并排除相应客户端集成。
- 核查日期 2026-09-23：Veil、Mekanism、Flux Networks、Polymorphic Energistics 未取得可验证的 NeoForge 26.1.2 版本。NeoEcoAE 旧版批次适配器与 CPU Mixin 暂不打包。保留的旧 `compileOnly` API 不代表对应运行时已验证。
- ExtendedAE 完整机器生产周期及 Jade 每个提示字段未逐项测试；CPU 测试覆盖序列化及认领，不覆盖异常断电恢复。
- 联合客户端中 MA、ExtendedAE、Applied Flux 自身创造栏重复物品导致 JEI 跳过对应栏的收集；Re:Avaritia 自带 `ae2_creative_energy_cell` 仍有旧配料格式错误。这些第三方问题未在本项目修补。
- ExtendedAE 的 `overload_processor` 配方保留模组加载条件，但移除了未安装 ExtendedAE 时无法解析的旧 `extendedae:config` 条件；关闭 ExtendedAE 对应配方开关不会再关闭 LT 此配方。
