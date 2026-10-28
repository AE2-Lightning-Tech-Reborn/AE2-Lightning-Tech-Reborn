# AE2 闪电科技 — Forge 1.20.1 移植版

[English](README.md)

这是 [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) 的附属模组，添加闪电能源、进阶机器和过载 ME 网络组件。

> GTO 专用构建 · 最低支持 GTO 0.6.0-dev-23 · Minecraft 1.20.1 / Java 21 / Forge 47.4.20+

本 `gto` 分支维护 Forge 1.20.1 GTO 适配，普通 `1.20.1` 分支用于同版本更新对照。当前版本为 LT `2.1.2-gto.12`，完整配套要求及验证边界见 [GTO 兼容说明](GTO_COMPATIBILITY.md)。

## 主要内容

- 高压闪电与极高压闪电两级能源。
- 闪电收集器、大气电离仪和特斯拉线圈能源链。
- 闪电装配室、闪电模拟室、过载处理工厂和水晶催化器加工链。
- 过载水晶生长、衰变和批量加工体系。
- 高吞吐的过载 ME 控制器、接口、样板供应器与线缆。
- 无线样板路由、天枢超级计算机自动化和可配置批量调度。
- 苍穹织雷模块化护甲与电磁炮系统。

## 依赖

| 模组 | 要求 |
|------|------|
| Minecraft 1.20.1 | 必需 |
| Java 21 / Forge 47.4.20+ | 必需 |
| GTO 整包 0.6.0-dev-23 或 dev-25 | 已验证服务端；使用对应整包的完整配套依赖 |
| GTOCore 26.10.10 或 26.10.18 | 必需，加载范围 `[26.10.10,26.10.11),[26.10.18]` |
| AE2-GTO 15.2610.31 或 15.2610.40 | 必需，只接受这两个精确版本 |
| 雷电核心：重生 2.0.3-gto.6 | 必需，精确版本 |
| NeoECO GTO 20.5.0-gto.1 或 20.5.2-gto.1 | 可选联动，只接受这两个精确版本 |
| JEI 或 EMI、Jade | 可选联动 |
| AdvancedAE、ExtendedAE、Applied Flux、AE2WTLib | 可选联动 |
| Mekanism、Curios、Flux Networks、Polymorph | 可选联动 |

所有可选联动均已隔离，不安装时不应影响正常启动。

客户端与服务端须一起更新上述配套。旧 GTOCore 26.10.5、AE2 15.2610.8、Core 2.0.3-gto.1 与当前 LT 不兼容；普通版 AE2、普通版 NeoECO 也不适用。最低整包版本不代表任意更新的依赖均兼容。

## 构建

使用 Java 21，先按 [GTO 构建说明](GTO_COMPATIBILITY.md#构建) 准备配套 Core，再执行：

```powershell
.\gradlew.bat test build
```

可发布制品位于 `build/libs/ae2lt-forge-1.20.1-2.1.2-gto.12.jar`。
带 `-slim.jar` 后缀的是开发中间制品，不应对外发布。

## 公开 API

`com.moakiee.ae2lt.api.*` 是提供给附属模组作者的稳定接口，包括闪电能源 capability 与事件、固定的方块实体和配方 ID，以及服务器权威的无线频率 API。Forge 事件订阅应使用 `MinecraftForge.EVENT_BUS`。

该包之外的实现均视为内部接口，可能在小版本更新时变化。

## 许可证

- 源代码：[GNU LGPL 3.0](LICENSE)
- 材质与视觉资源：[CC BY-NC-SA 3.0](LICENSE_ASSETS.md)
- 改编或内置代码：[第三方声明](THIRD_PARTY_NOTICES.md)

开发者：**MOAKIEE**、**CystrySU**、**gjmhmm8**、**_leng**、**TedXenon**、**MHanHanBing**。
