---
navigation:
  title: Overloaded ME Controller
  icon: ae2lt:overloaded_controller
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_controller
---

# Overloaded ME Controller

<ItemImage id="ae2lt:overloaded_controller" scale="2" float="left" />

The **Overloaded ME Controller** is a high-capacity replacement for AE2's <ItemLink id="ae2:controller" /> and the structural base shared by the [Wireless and Advanced Wireless Overloaded Controllers](wireless-network.md).

## Core Specifications

* **Channel Output**: Provides **128 channels** per controller block under default settings (scaled by AE2's active channel mode).
* **Energy Buffer & Passive Generation**: Holds **16,000,000 AE** internally and passively generates **100 AE/t** by default to supply the connected ME grid.
* **Multiblock Rules**: Follows standard AE2 controller geometry rules (maximum 7 blocks along any axis, no invalid cross-axis clustering, one contiguous controller multiblock per network).

## Mixing & Channel Notes

* **Cannot Mix with Vanilla ME Controllers**: An ME network **cannot contain both Overloaded ME Controllers and vanilla AE2 ME Controllers**. Mixing them immediately forces a controller conflict (`CONTROLLER_CONFLICT`). Replace all vanilla controllers when upgrading a network.
* **Mixing Overloaded Variants**: **Overloaded ME Controllers**, **Wireless Overloaded Controllers**, and **Advanced Wireless Overloaded Controllers** belong to the same controller family and may be combined freely in one multiblock.
* **Effective Channels**: Total capacity scales with controller count and AE2's channel mode, while downstream availability remains bounded by the complete cable path. Run [Overloaded Cables](overloaded-cable.md) directly off controller faces to use full trunk capacity.
