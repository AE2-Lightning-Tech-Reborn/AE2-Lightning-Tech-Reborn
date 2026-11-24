---
navigation:
  title: 过载网络
  icon: ae2lt:overloaded_controller
  parent: index.md
  position: 50
---

# 过载网络

**过载网络**面向大规模自动化基地，由高容量主干频道、无线网格覆盖与大吞吐自动化设备组成。

<SubPages />

## 按用途选择设备

本板块的设备与工具按功能分为三条清晰路径，使用时请注意区分各自适用的入口：

| 用途 | 核心设备 / 工具 | 特点 |
|------|-----------------|------|
| **高容量主干与频道** | [过载ME控制器](overloaded-controller.md)、[过载线缆](overloaded-cable.md) | 扩展控制器频道与主干承载能力，保留 AE2 控制器多方块规则 |
| **远程 ME 网格与无线终端** | [无线网络](wireless-network.md)、[过载频率卡](overloaded-frequency-card.md) | 通过频率广播实现同维或跨维 ME 网格互联、无线终端覆盖与频率卡直连 |
| **自动化发配、接口与搬运** | [过载样板供应器](overloaded-pattern-provider.md)、[扩展过载样板供应器](extended-overloaded-pattern-provider.md)、[过载ME接口](overloaded-interface.md)、[过载 IO 端口](overloaded-io-port.md) | 借助**过载无线连接工具**管理同维点对点物资收发与大吞吐元件迁移 |

<OptionalSection modId="appflux">
### 无线 FE 供电（Applied Flux）

安装 Applied Flux 后，可从 ME 网络提取 FE 并通过三条途径向机器供电：[过载供电仪](overloaded-power-supply.md)（同维高密度集中供电）、[过载频率卡](overloaded-frequency-card.md)（跨维远程供电），以及在过载ME接口或过载样板供应器中安装感应卡向目标供能。
</OptionalSection>
