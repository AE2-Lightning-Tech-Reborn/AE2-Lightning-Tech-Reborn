---
navigation:
  title: Tianshu Matter Warping Matrix
  icon: ae2lt:matter_warping_matrix_controller
  parent: index.md
  position: 45
---

# Tianshu Matter Warping Matrix

The **Tianshu Matter Warping Matrix** is a large-scale crafting multiblock built to store and execute **Molecular Assembler-compatible patterns** (crafting, stonecutting, smithing, etc.) in massive parallel batches, replacing sprawling walls of Molecular Assemblers.

> Role separation: the **Matter Warping Matrix** stores patterns and executes Molecular Assembler-compatible crafts, while crafting CPUs (such as the [Tianshu Supercomputer](../tianshu/tianshu-index.md)) plan and schedule crafting jobs.

<SubPages />

## Key Features

* **Fixed Footprint**: 7×11×7 multiblock connecting to the ME network through a single port, consuming only **1 channel** total.
* **Power**: **8 AE/t** idle drain plus **1 AE** per accepted craft copy (insufficient network power caps copies executed per tick).
* **Scalable Pattern Storage**: 50 pattern bay positions accepting any mix of T1 (36 slots), T2 (72 slots), and T3 (288 slots) Pattern Storages—up to **14,400** slots at full T3—with in-place upgrades and one-click pattern migration from the controller.
* **Configurable Core Bay**: An 81-block core chamber holding 1 Main Core and 80 peripheral units to balance thread throughput, amplification, and thermal control.
