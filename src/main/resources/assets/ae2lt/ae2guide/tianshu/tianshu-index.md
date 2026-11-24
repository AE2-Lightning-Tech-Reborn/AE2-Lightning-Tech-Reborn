---
navigation:
  title: Tianshu Supercomputer
  icon: ae2lt:tianshu_supercomputer_controller
  parent: index.md
  position: 46
---

# Tianshu Supercomputer

The **Tianshu Supercomputer** is a high-performance multiblock crafting CPU for ME networks, combining a shared crafting storage pool, concurrent multi-job dispatching, closed-loop automation, and an inventory maintenance engine.

<SubPages />

## Key Features

* **Fixed Footprint**: 7×7×7 multiblock connected via a single ME port using **1 channel**.
* **Shared Crafting Pool**: Concurrent crafting jobs share total crafting storage and per-tick successful dispatch budget while each job receives its own copy budget.
* **Configurable Core Chamber**: A 3×3×3 core bay (27 blocks) holding 1 Main Core and 26 units. You can start early with a crafting-table **Baseline Main Core** surrounded by Blank Units.
* **Closed-Loop & Inventory Maintenance**: The 17 cooling-compatible shell positions freely accept Cooling Units, **Closed-Loop Pattern Storages**, and **Closed-Loop Seed Storages**. Every valid Main Core supports closed-loop planning and up to **2,048** inventory maintenance rules (configured via the [Tianshu Pattern Encoding Terminal](../terminals/pattern-encoding-terminal.md)).
* **Algorithm Selection**: Use the **CPU Algorithm Selection** button in the Controller UI to inspect or switch planning algorithms and priorities (defaults to the Thunderbolt V2 planning engine).
