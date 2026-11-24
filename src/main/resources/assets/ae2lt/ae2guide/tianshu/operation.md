---
navigation:
  title: Port, Jobs & Algorithm Selection
  icon: ae2lt:tianshu_supercomputer_port
  parent: tianshu/tianshu-index.md
  position: 30
item_ids:
  - ae2lt:tianshu_supercomputer_controller
  - ae2lt:tianshu_supercomputer_port
---

# Port, Jobs & Algorithm Selection

<ItemGrid>
  <ItemIcon id="ae2lt:tianshu_supercomputer_controller" />
  <ItemIcon id="ae2lt:tianshu_supercomputer_port" />
</ItemGrid>

## Port Connection & Online Checks

* **ME Connection**: Once formed, the **Tianshu Supercomputer Port** connects on all six faces using **1 channel**. Idle power depends on the Main Core (Baseline 256 AE/t, Quantum 8,192 AE/t, Overload 262,144 AE/t, Multidimensional 8 AE/t).
* **Readiness Checklist**: If jobs fail to start or terminals cannot detect the supercomputer, verify in order:
  1. The Controller reports the structure as formed.
  2. The Port is connected to the ME grid with an active channel and sufficient AE power.
  3. Every chunk spanned by the 7×7×7 multiblock is loaded.
* **Disconnect Recovery**: Unloading part of the structure or breaking a block pauses job entry; saved in-progress jobs resume once the multiblock reconnects.

## CPU Algorithm Selection & Priority

The **Tianshu Supercomputer Controller** UI exposes **CPU Algorithm Selection** on the left toolbar and **CPU Priority** in the top-right corner:

* **Planning Phase**: Clicking **CPU Algorithm Selection** opens the algorithm menu to inspect or switch planning engines and set algorithm priority. Controllers initialize with **Thunderbolt V2** at priority `0`, and the Port mirrors the Controller's saved configuration.
* **Execution Phase**: Algorithm selection only affects **pre-submission planning**; once a job starts, execution is governed by the supercomputer's shared storage pool, per-tick dispatch cap, and per-job copy budget.
* **CPU Priority**: Determines selection order when multiple crafting CPUs share the same ME network.

## Concurrent Jobs & Storage Reservation

* Multiple jobs run concurrently on one Tianshu Supercomputer. Each accepted job reserves the crafting storage required by its plan and releases it upon completion or cancellation.
* Active jobs share the global per-tick successful dispatch cap while tracking individual per-job copy budgets.
