---
navigation:
  title: Main Cores, Peripheral Units & Heat
  icon: ae2lt:matter_warping_matrix_stable_main_core
  parent: matrix/matrix-index.md
  position: 20
item_ids:
  - ae2lt:matter_warping_matrix_stable_main_core
  - ae2lt:matter_warping_matrix_quantum_main_core
  - ae2lt:matter_warping_matrix_overload_main_core
  - ae2lt:matter_warping_matrix_multidimensional_main_core
  - ae2lt:matter_warping_matrix_thread_unit_t1
  - ae2lt:matter_warping_matrix_thread_unit_t2
  - ae2lt:matter_warping_matrix_thermal_control_unit_t1
  - ae2lt:matter_warping_matrix_thermal_control_unit_t2
---

# Main Cores, Peripheral Units & Heat

The core chamber holds **81 blocks**: **1 Main Core** at the exact center and **80 valid peripheral units** filling the surrounding positions. The scene below hides the shell to show one valid **Quantum Main Core** layout (1 Quantum Main Core, 6 adjacent Thermal Control Unit T1s, 10 Tianshu Amplifier Units, and 64 Thread Unit T1s).

<GameScene zoom="3" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/matter_warping_matrix_core.snbt" />
  <BoxAnnotation min="3 5 3" max="4 6 4" color="#f2d37a" alwaysOnTop={true}>Main Core Position</BoxAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## Main Core Tiers

| Main Core | Copy Cap / Tick | Allowed Amplifiers | Thermal Efficiency |
|-----------|----------------:|--------------------|--------------------|
| **Stable Main Core** | 4,096 | 0 (unsupported) | Keep cool; `clamp(1 − h, 0.45, 1)` (45% floor) |
| **Quantum Main Core** | 122,880 | 0–15 | Keep cool; `clamp(1 − h, 0.45, 1)` (45% floor) |
| **Overload Main Core** | 4,194,304 | 0–15 | Peaks at **50%** heat; `0.05 + 0.95 × [4h(1 − h)]⁵` |
| **Multidimensional Main Core** | Unbounded copies | 0 (all 80 slots must be Tianshu Blank Units) | Fixed 100%, ignores heat |

## Peripheral Units

| Unit | Contribution | Notes & Restrictions |
|------|--------------|----------------------|
| **Thread Unit T1** | Stable `1,024`; Quantum/Overload `1` thread pt | Raises base throughput; finite cores require at least 1 Thread Unit |
| **Thread Unit T2** | Stable `3,584`; Quantum/Overload `2` thread pts | Higher density per core slot |
| **Tianshu Amplifier Unit** | `R + 1` (shared block with Supercomputer) | Quantum and Overload cores only; max **15** (16th prevents formation) |
| **Thermal Control Unit T1** | `1` cooling pt | Increases heat capacity and cooling rate; decays with distance from center |
| **Thermal Control Unit T2** | `2` cooling pts | Double cooling points with the same distance decay |
| **Tianshu Blank Unit** | — | Filler block; mandatory for all 80 peripheral slots with a Multidimensional Main Core |

## Throughput Calculation

Effective craft copies per tick are computed in three steps:

1. **Base Capacity**:
   * **Stable**: `1,024 × T1 + 3,584 × T2` (capped at `4,096`).
   * **Quantum**: `256 × P × R²`, where `P = T1 + 2 × T2` thread points and `R = 1 + Amplifiers`.
   * **Overload**: `256 × P × R³`.
2. **Tier Cap**: Clamped to the Main Core's per-tick copy cap.
3. **Thermal Efficiency**: Multiplied by the current thermal efficiency and rounded down.

> Regardless of core tier (including Multidimensional), the matrix is capped at **16,384** craft invocations per tick in addition to material and AE power limits. Matrix throughput is independent of the Tianshu Supercomputer's dispatch and copy budgets.

## Thermal Distance & Heat Formula

Thermal Control Units scale by **Manhattan distance** (`|dx| + |dy| + |dz|`) from the central Main Core:

| Manhattan Distance | Effective Weight |
|-------------------:|-----------------:|
| 1 | 100% |
| 2 | 75% |
| 3 | 50% |
| 4 | 25% |
| 5+ | 0% |

* **Effective Cooling**: `C = Σ(cooling points × distance weight)` (T1 = 1, T2 = 2).
* **Heat Capacity**: `K = 2048 + 150 × C`.
* **Cooling Rate per Tick**: `r = 0.00008 + 0.000025 × C`.
* **Displayed Heat**: `h = clamp(H / K, 0, 1)`.
* **Tick Update**: Heat is added from load, then dissipated proportionally:
  `H_next = (H_current + P × g × L) × (1 − r)`
  * `P` is total thread points; `L` is `accepted copies / available copies` clamped to `[0, 1]` (`0` when idle).
  * Heat factor `g = 0.256` for Stable/Quantum, and `g = 1.2032` for Overload.
