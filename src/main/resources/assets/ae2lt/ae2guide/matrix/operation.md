---
navigation:
  title: Patterns, Port & Operation
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

# Patterns, Port & Operation

<ItemGrid>
  <ItemIcon id="ae2lt:matter_warping_matrix_pattern_storage_t1" />
  <ItemIcon id="ae2lt:matter_warping_matrix_pattern_storage_t2" />
  <ItemIcon id="ae2lt:matter_warping_matrix_pattern_storage_t3" />
  <ItemIcon id="ae2lt:matter_warping_matrix_pattern_storage_upgrade" />
  <ItemIcon id="ae2lt:dense_topological_lattice" />
</ItemGrid>

## Pattern Storage Tiers & In-Place Upgrades

The 50 bay slots accept any combination of three storage tiers:

| Pattern Storage | Slots per Block | Total at 50 Blocks |
|-----------------|----------------:|-------------------:|
| **Pattern Storage T1** | 36 | 1,800 |
| **Pattern Storage T2** | 72 | 3,600 |
| **Pattern Storage T3** | 288 | 14,400 |

* **Supported Pattern Types**: Each slot holds 1 decoded pattern belonging to **Molecular Assembler-compatible categories** (crafting, stonecutting, smithing, etc.). Processing patterns for external machines are not accepted.
* **In-Place Upgrades**: Trigger upgrades directly from the Controller UI without dismantling the structure; stored patterns remain intact:
  * Consumes **Pattern Storage Upgrade** from your inventory to upgrade **T1 → T2**.
  * Consumes **Dense Topological Lattice** from your inventory to upgrade **T2 → T3**.
  * A T1 bay upgraded to T2 during one action will not immediately chain-upgrade to T3 in the same pass. Breaking a bay block directly drops its stored patterns.

## Port Connection & Pattern Management

* **ME Connection & Power**: Once formed, the **Matter Warping Matrix Port** connects on all six faces using **1 channel**, **8 AE/t** idle, and **1 AE** per accepted craft copy. Unforming or partial chunk unloading halts supply until chunks reload and rescan.
* **Management Interfaces**:
  * **Port GUI**: Browse, search, and edit patterns bay by bay.
  * **ME Pattern Access Terminal**: Exposes the aggregated pattern inventory through the port.
  * **Item Automation**: Inserting valid patterns via item capability routes them into available writable bay slots.

## Migrate Patterns

When the matrix is formed and online, open **Migrate Patterns** in the Controller UI to move eligible Molecular Assembler-compatible patterns from supported sources across the same ME network into the matrix:

* **Idle Requirement**: The network and target sources must be idle; active crafting jobs pause or block migration.
* **Deduplication & Stopping**: Moves only valid physical patterns when free bay slots exist, recycles duplicate patterns automatically, and tracks moved vs. recycled counts separately. Clicking **Stop Migration** preserves already completed moves.
* **Recovery**: Reports skipped pattern disks, non-standard slots, insertion failures, or source changes by category. Any returned patterns that could not fit into ME storage or your inventory can be claimed by reopening the migration UI after freeing space.
