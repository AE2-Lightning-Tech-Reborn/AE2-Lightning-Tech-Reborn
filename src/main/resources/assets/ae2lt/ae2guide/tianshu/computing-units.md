---
navigation:
  title: Main Cores, Computing Units & Performance
  icon: ae2lt:tianshu_baseline_main_core
  parent: tianshu/tianshu-index.md
  position: 20
item_ids:
  - ae2lt:tianshu_baseline_main_core
  - ae2lt:tianshu_quantum_main_core
  - ae2lt:tianshu_overload_main_core
  - ae2lt:tianshu_multidimensional_main_core
  - ae2lt:tianshu_blank_unit
  - ae2lt:storage_supercomputing_unit
  - ae2lt:parallel_supercomputing_unit
  - ae2lt:tianshu_amplifier_unit
  - ae2lt:closed_loop_pattern_storage
  - ae2lt:closed_loop_seed_storage
---

# Main Cores, Computing Units & Performance

The central **3×3×3 core chamber** holds 27 blocks: 1 Main Core in the center surrounded by **26** valid units.

<GameScene zoom="4" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/tianshu_supercomputer_core.snbt" />
  <BoxAnnotation min="3 3 3" max="4 4 4" color="#f2d37a" alwaysOnTop={true}>Main Core Position</BoxAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## Main Core Tiers

| Main Core | Built-In Storage | Dispatch Cap / Tick | Copy Cap / Tick / Job | Allowed Amplifiers | Port Idle Drain |
|-----------|-----------------:|--------------------:|----------------------:|--------------------|----------------:|
| **Baseline Main Core** | 1 MiB | 512 | 1,024 | 0 | 256 AE/t |
| **Quantum Main Core** | 256 MiB | 6,144 | 20,480 | 0–15 | 8,192 AE/t |
| **Overload Main Core** | 64 GiB | 16,384 | 4,194,304 | 0–15 | 262,144 AE/t |
| **Multidimensional Main Core** | Infinite | 16,384 | Infinite | 0 (26 Tianshu Blank Units) | 8 AE/t |

> **Starter Setup**: The **Baseline Main Core** can be crafted on a crafting table and runs even with 26 **Tianshu Blank Units**, yielding **1 MiB** storage, **128** successful dispatches/t, and **256** batch copies/t. Adding 1 Parallel Unit doubles dispatches to 256/t, and 3 Parallel Units reach the 512/t Baseline cap.

## Performance Formulas

Let `S` = Storage Units, `P` = Parallel Units, `A` = Tianshu Amplifier Units, and `R = 1 + A` (Multidimensional uses its own fixed profile):

| Main Core | Dispatch Multiplier | External Storage Multiplier | Copy Multiplier |
|-----------|---------------------|-----------------------------|-----------------|
| **Baseline** | `1` | `1` | `2` |
| **Quantum** | `2R` | `2R` | `R` |
| **Overload** | `2R` | `(2R)²` | `R²` |

* **Total Crafting Storage**: `Built-In + 64 MiB × S × External Storage Multiplier`. Each accepted job reserves its planned storage and frees it upon completion or cancellation.
* **Successful Dispatches / Tick**: `128 × (1 + P) × Dispatch Multiplier` (clamped by tier cap), shared across all active jobs.
* **Per-Job Copy Budget / Tick**: Compatible batch targets scale each successful dispatch by the Copy Multiplier up to the per-job cap; single-item processing machines and batch-capable targets (such as the Matter Warping Matrix) advance at their respective rates. Every copy still consumes full ingredients and produces full outputs.

### Example Verification (Visible in Controller UI)

With a **Quantum Main Core**, `S = 6`, `P = 5`, and `A = 15` (`R = 16`):

* **Crafting Storage**: `256 MiB + 64 MiB × 6 × 32 = 12,544 MiB` (~`12.25 GiB`).
* **Successful Dispatches**: `128 × 6 × 32 = 24,576`, clamped to **6,144/t** (marked capped in UI).
* **Per-Job Batch Copies**: Clamped to the Quantum cap of **20,480/t**.

## Closed-Loop Storage Bays

<ItemGrid>
  <ItemIcon id="ae2lt:closed_loop_pattern_storage" />
  <ItemIcon id="ae2lt:closed_loop_seed_storage" />
</ItemGrid>

**Closed-Loop Pattern Storages** and **Closed-Loop Seed Storages** occupy the 17 cooling-compatible shell slots and **do not** take up core chamber unit slots:

* **Closed-Loop Pattern Storage**: **36** closed-loop pattern slots per block.
* **Closed-Loop Seed Storage**: **10** ME storage cell slots per block; insert cells matching your seed types and use Refill Seeds in the [Tianshu Pattern Encoding Terminal](../terminals/pattern-encoding-terminal.md).
* **Availability**: Every valid Main Core (including Baseline) supports closed-loop planning and inventory maintenance (up to **2,048** rules).
