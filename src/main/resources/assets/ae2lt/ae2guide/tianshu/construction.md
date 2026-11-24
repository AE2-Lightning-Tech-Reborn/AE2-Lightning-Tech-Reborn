---
navigation:
  title: Construction & Auto-Build
  icon: ae2lt:tianshu_supercomputer_casing
  parent: tianshu/tianshu-index.md
  position: 10
item_ids:
  - ae2lt:tianshu_supercomputer_casing
  - ae2lt:phase_change_cooling_unit
  - ae2lt:tianshu_supercomputer_glass
---

# Construction & Auto-Build

The Tianshu Supercomputer is a fixed **7×7×7** multiblock. Place the **Controller** at the bottom-center edge of one face; the structure extends **6 blocks** backward, **3 blocks** to each side, and **6 blocks** upward.

<GameScene zoom="2.8" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/tianshu_supercomputer.snbt" />
  <DiamondAnnotation pos="6.5 0.5 3.5" color="#f2d37a">Tianshu Supercomputer Controller</DiamondAnnotation>
  <DiamondAnnotation pos="3.5 6.5 3.5" color="#85f29e">Default Port Position</DiamondAnnotation>
  <DiamondAnnotation pos="3.5 0.5 3.5" color="#80c6ff">Alternate Port Position</DiamondAnnotation>
  <DiamondAnnotation pos="2.5 6.5 2.5" color="#ff9fd7">Example Closed-Loop Pattern Storage</DiamondAnnotation>
  <DiamondAnnotation pos="4.5 6.5 2.5" color="#9fffe1">Example Closed-Loop Seed Storage</DiamondAnnotation>
  <BoxAnnotation min="2 2 2" max="5 5 5" color="#d58cff" alwaysOnTop={true}>3×3×3 Core Chamber</BoxAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## Structure & Bill of Materials

A complete structure consists of **243** blocks:

| Component | Count & Rules |
|-----------|---------------|
| Tianshu Supercomputer Controller | 1 (placed manually at bottom-center edge) |
| Tianshu Supercomputer Casing | 99 |
| Tianshu Supercomputer Glass | 98 |
| Tianshu Supercomputer Port | **Exactly 1** in either the top or bottom candidate slot (Auto-Build defaults to top if both are empty) |
| Cooling-Compatible Slots | **17** positions; each accepts a **Cooling Unit**, **Closed-Loop Pattern Storage**, or **Closed-Loop Seed Storage** |
| 3×3×3 Core Chamber | **27** blocks: 1 Main Core in the center (such as the starter **Baseline Main Core**) + 26 valid peripheral units |

## Auto-Build Procedure

1. Place the **Tianshu Supercomputer Controller** manually.
2. Carry the **215** shell blocks in your inventory (99 Casing + 98 Glass + 17 Cooling-compatible blocks + 1 Port).
3. Open the Controller UI and click **Auto-Build**:
   * Pre-placed valid blocks (such as a bottom Port or Closed-Loop storage bays) are preserved.
   * Obstructions are reported; if materials run out or a block obstructs placement, auto-build stops while keeping completed blocks.
4. Assemble the **27-block core chamber manually**; once finished, the structure forms automatically.
5. Inspect uses of the Controller in your recipe viewer for layer-by-layer structure previews.
