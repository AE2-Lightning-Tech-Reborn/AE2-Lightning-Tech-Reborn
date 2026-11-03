---
navigation:
  title: Patterns, Port, and Operation
  icon: ae2lt:matter_warping_matrix_pattern_storage_t1
  parent: matrix/matrix-index.md
  position: 30
item_ids:
  - ae2lt:matter_warping_matrix_pattern_storage_t1
  - ae2lt:matter_warping_matrix_pattern_storage_t2
  - ae2lt:matter_warping_matrix_pattern_storage_t3
  - ae2lt:matter_warping_matrix_pattern_storage_upgrade
  - ae2lt:dense_topological_lattice
---

# Patterns, Port, and Operation

## Pattern Storage

The structure provides 50 optional Pattern Storage positions. At least one storage is required for the matrix to form.

| Pattern Storage | Capacity each | Total with all 50 installed |
|-----------------|--------------:|----------------------------:|
| T1 | 36 | 1,800 |
| T2 | 72 | 3,600 |
| T3 | 288 | 14,400 |

T1, T2, and T3 storages may be mixed in one structure. The total capacity is the sum of every installed storage, with one encoded pattern per slot.

## Inserting and Removing Patterns

Once the matrix is formed and connected to an active ME network, all of its pattern slots appear as a group represented by the Matrix Port in the **Pattern Access Terminal**. Patterns can be inserted, replaced, or removed there without opening the outer structure.

Right-clicking the Matrix Port opens a **pattern management** screen. Use the arrows in the upper left to select a warehouse, then browse, search, and manage its patterns. Only one warehouse is synchronized at a time, even in a full 14,400-slot matrix.

The Matrix Port also exposes item insertion for automation. Item pipes can insert patterns into available slots. Breaking a Pattern Storage drops its stored patterns; upgrading it in place from the Controller preserves them.

## Upgrading Pattern Storage

Place Tianshu Matter Warping Matrix Pattern Storage Upgrades in the player inventory, then use **Upgrade** from the Controller. The matrix replaces T1 storages with T2 storages in place, limited by the number of upgrades carried:

* One upgrade item is consumed per upgraded storage
* Stored patterns are preserved
* If too few upgrades are available, only the affordable number is upgraded
* No upgrade items are consumed when no T1 storages remain

Each **Dense Topological Lattice** in the player inventory also upgrades one existing T2 warehouse to T3 in place. A T1 upgraded during the same action remains T2 until the next action. Both upgrades preserve stored patterns. A T3 warehouse can also be crafted directly from a T2 warehouse and one Dense Topological Lattice.

## Matrix Port

Exactly one Port must be installed. Any of the three candidate positions is valid. When no existing Port is found, auto-build places it directly opposite the Controller.

The Port:

* Connects the internal AE nodes of the Pattern Storages to the ME network; each warehouse advertises its own patterns and receives its matching crafting requests
* Provides shared network access, pattern management, and automated insertion for all warehouses
* Lets the warehouse validate each request before the matrix executes it and returns results to the network
* Accepts ME cable connections on all six sides only after formation; cables cannot connect while the structure is unformed. It has a fixed idle cost of 8 AE/t, and the formed multiblock consumes 1 channel

In addition to its fixed idle cost, the matrix consumes **1 AE** for every pattern copy it successfully accepts. If it accepts a given number of pattern copies during one tick, that tick consumes the same amount of additional AE on top of the 8 AE/t idle cost. When available power is insufficient, the matrix limits that tick's work to the number of copies the network can pay for. It accepts no new crafting work while the Port is disconnected from a powered network or belongs to an unformed structure.

The matrix does not force-load any chunk in its footprint. If any structure chunk is unloaded, the Port disconnects and work pauses without clearing patterns, jobs, or the last confirmed structure data. It rescans and reconnects automatically after the complete structure is loaded again. A cross-chunk build therefore needs a chunk loader covering the whole matrix.

## Troubleshooting

* **Structure does not form:** Check the central Main Core, all 80 Peripheral Units, at least one Pattern Storage, the unique Port, and every fixed shell position
* **Auto-build does nothing:** Remove invalid blocks from target positions and verify that all materials are in the player inventory
* **Port is absent from the network:** Check the cable connection, network power, and formation state
* **Port stays offline after a cross-chunk load:** Ensure the chunk loader covers the entire matrix and allow time for the automatic rescan
* **A stored pattern is not selected for a job:** Confirm that the pattern is stored in this matrix and that the Port is online, then check for other providers advertising the same pattern
