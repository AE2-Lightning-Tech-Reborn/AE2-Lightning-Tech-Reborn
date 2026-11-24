---
navigation:
  title: Construction & Auto-Build
  icon: ae2lt:matter_warping_matrix_casing
  parent: matrix/matrix-index.md
  position: 10
item_ids:
  - ae2lt:matter_warping_matrix_casing
  - ae2lt:matter_warping_matrix_constraint_frame
  - ae2lt:matter_warping_matrix_glass
  - ae2lt:matter_warping_matrix_controller
  - ae2lt:matter_warping_matrix_port
---

# Construction & Auto-Build

The Tianshu Matter Warping Matrix is a fixed **7×11×7** multiblock. The **Controller** sits at the exact center of one 7×11 face, with the structure extending **6 blocks** backward, **3 blocks** to each side, and **5 blocks** up and down.

<GameScene zoom="2.4" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/matter_warping_matrix.snbt" />
  <DiamondAnnotation pos="0.5 5.5 3.5" color="#f2d37a">Matter Warping Matrix Controller</DiamondAnnotation>
  <DiamondAnnotation pos="6.5 5.5 3.5" color="#85f29e">Default Port Position</DiamondAnnotation>
  <DiamondAnnotation pos="1.5 1.5 1.5" color="#80c6ff">Example Pattern Storage Bay</DiamondAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## Structure & Bill of Materials

A minimum valid matrix (with 1 Pattern Storage) uses **434** blocks in total:

| Component | Count & Rules |
|-----------|---------------|
| Matter Warping Matrix Controller | 1 (placed manually at the center of one 7×11 face) |
| Matter Warping Matrix Casing | 174 |
| Matter Warping Matrix Constraint Frame | 132 (includes the 2 unused port candidate positions) |
| Matter Warping Matrix Glass | 44 |
| Matter Warping Matrix Port | **Exactly 1** among 3 candidate slots (defaults directly opposite the Controller, or either side candidate) |
| Pattern Storage (T1 / T2 / T3) | **50** candidate positions in the upper/lower bays; optional empty slots, **at least 1 required**, T1/T2/T3 may be mixed freely |
| Core Chamber (81 blocks) | 1 Main Core in the geometric center + 80 valid peripheral units (finite cores require at least 1 Thread Unit; Multidimensional requires 80 Tianshu Blank Units) |

> Only wrong blocks occupying required structural positions block formation or auto-build; designated empty interior spaces may remain air.

## Auto-Build Procedure

1. Place the **Matter Warping Matrix Controller** manually and keep required structural positions clear.
2. Carry the shell materials in your inventory (minimum **352** blocks: 174 Casing + 132 Constraint Frame + 44 Glass + 1 Port + at least 1 Pattern Storage; any additional T1/T2/T3 Pattern Storages in your inventory are placed up to 50).
3. Open the Controller UI and trigger **Auto-Build**:
   * Existing valid blocks and an already placed Port are preserved; if all 3 port positions are empty, the Port is placed opposite the Controller.
   * Assemble the **81-block core chamber manually**; once complete, the multiblock scans and forms automatically.
4. Use your recipe viewer (JEI / EMI) for layer-by-layer structure previews and full material counts.
