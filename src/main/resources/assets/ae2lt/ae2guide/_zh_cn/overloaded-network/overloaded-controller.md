---
navigation:
  title: 过载ME控制器
  icon: ae2lt:overloaded_controller
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_controller
---

# 过载ME控制器

<ItemImage id="ae2lt:overloaded_controller" scale="2" float="left" />

**过载ME控制器**是原版 AE2 <ItemLink id="ae2:controller" /> 的高容量替代方块，同时也是 [无线过载控制器与高级无线过载控制器](wireless-network.md) 的基础形态。

## 核心参数

* **频道容量**：默认配置下，每个过载ME控制器方块可提供 **128 频道**（受 AE2 当前频道模式倍率影响）。
* **能量缓存与被动供能**：内置 **16,000,000 AE** 能量缓存，并默认以 **100 AE/t** 被动补充自身缓存、持续向所连 ME 网格供电。
* **多方块规则**：保留 AE2 控制器的多方块尺寸与形状规则（单轴最长 7 格、禁止单个控制器方块被三个以上方向包夹、同一网络只能存在一座连续的控制器结构）。

## 混搭与频道说明

* **禁止与原版 ME控制器混用**：同一个 ME 网络中**不能同时存在过载ME控制器与原版 AE2 ME控制器**，混搭会直接触发控制器冲突（`CONTROLLER_CONFLICT`）导致网络停摆。若需从原版控制器升级，请整网替换为过载系列控制器。
* **过载系列内部可混拼**：**过载ME控制器**、**无线过载控制器**与**高级无线过载控制器**同属过载控制器体系，可在同一座控制器多方块中自由混拼。
* **有效频道**：在默认 AE2 频道模式下，纯过载控制器结构的总频道上限按方块数与单块容量计算；若服务器修改了 AE2 频道模式，或下游路径存在普通线缆瓶颈，最终可用频道数会受频道倍率与完整传输路径共同制约。建议直接从控制器引出 [过载线缆](overloaded-cable.md) 作为主干。
