---
navigation:
  title: 过载线缆
  icon: ae2lt:overloaded_cable
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_cable
  - ae2lt:overloaded_cable_white
  - ae2lt:overloaded_cable_orange
  - ae2lt:overloaded_cable_magenta
  - ae2lt:overloaded_cable_light_blue
  - ae2lt:overloaded_cable_yellow
  - ae2lt:overloaded_cable_lime
  - ae2lt:overloaded_cable_pink
  - ae2lt:overloaded_cable_gray
  - ae2lt:overloaded_cable_light_gray
  - ae2lt:overloaded_cable_cyan
  - ae2lt:overloaded_cable_purple
  - ae2lt:overloaded_cable_blue
  - ae2lt:overloaded_cable_brown
  - ae2lt:overloaded_cable_green
  - ae2lt:overloaded_cable_red
  - ae2lt:overloaded_cable_black
---

# 过载线缆

<ItemGrid>
  <ItemIcon id="ae2lt:overloaded_cable" />
  <ItemIcon id="ae2lt:overloaded_cable_white" />
  <ItemIcon id="ae2lt:overloaded_cable_light_blue" />
  <ItemIcon id="ae2lt:overloaded_cable_orange" />
  <ItemIcon id="ae2lt:overloaded_cable_purple" />
  <ItemIcon id="ae2lt:overloaded_cable_black" />
</ItemGrid>

**过载线缆**是高容量致密型 ME 线缆，包含默认的福鲁伊克斯色与 16 种染色变体（共 17 色），遵循 AE2 同色互联、福鲁伊克斯色与任意颜色互联的规则，亦可通过 [彩虹猪咪](../materials/rainbow-pigmee.md) 快速换色。

## 混合拓扑与路径瓶颈说明

* **高容量主干**：在过载ME控制器与过载线缆构成的主干路径上，可承载远超普通 ME致密线缆的高容量频道流量。
* **普通节点与支线限制**：一旦路径中接入普通 AE2 线缆（如 ME玻璃线缆、ME包层线缆、ME智能线缆、ME致密包层线缆、ME致密线缆）或普通 AE2 透传方块，该段路径仍受其原有频道上限与 AE2 频道模式约束。
* **排查建议**：当远端设备提示频道不足时，请沿「控制器 → 主干线缆 → 支线/透传设备 → 目标设备」整条路径逐段排查，确认是否存在普通线缆或低容量设备形成瓶颈；不要将多个高耗频道分支串联在同一条普通支线上。
