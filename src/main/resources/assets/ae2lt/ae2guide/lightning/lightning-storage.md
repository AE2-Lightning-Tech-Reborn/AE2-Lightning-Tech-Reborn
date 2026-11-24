---
navigation:
  title: Lightning Storage Cells
  icon: ae2lt:lightning_storage_component_i
  parent: lightning/lightning-index.md
item_ids:
  - ae2lt:lightning_storage_component_i
  - ae2lt:lightning_storage_component_ii
  - ae2lt:lightning_storage_component_iii
  - ae2lt:lightning_storage_component_iv
  - ae2lt:lightning_storage_component_v
  - ae2lt:bulk_lightning_storage_component
  - ae2lt:bulk_lightning_cell_component
  - ae2lt:lightning_item_cell_housing
  - ae2lt:infinite_storage_cell
  - ae2lt:lightning_cell_component_i
  - ae2lt:lightning_cell_component_ii
  - ae2lt:lightning_cell_component_iii
  - ae2lt:lightning_cell_component_iv
  - ae2lt:lightning_cell_component_v
---

# Lightning Storage Cells

<ItemGrid>
  <ItemIcon id="ae2lt:lightning_item_cell_housing" />
  <ItemIcon id="ae2lt:lightning_cell_component_i" />
  <ItemIcon id="ae2lt:lightning_storage_component_i" />
  <ItemIcon id="ae2lt:lightning_storage_component_iii" />
  <ItemIcon id="ae2lt:lightning_storage_component_v" />
  <ItemIcon id="ae2lt:bulk_lightning_storage_component" />
</ItemGrid>

Lightning resources are stored inside an <ItemLink id="ae2:drive" /> or <ItemLink id="ae2:chest" /> using dedicated Lightning Storage Cells.

`Using the spatial power of the End and the natural Lightning affinity of Overload Crystal, high-energy Lightning is constrained inside a tiny cell.`

## Components, Housing, and Cells

Keep the distinction between **Components** and **Cells** in mind when crafting:

* **Lightning Storage Components (I–V) / Bulk Lightning Storage Component**: Crafting cores that cannot be inserted directly into a drive.
* **Lightning Item Cell Housing**: Combine with a component to assemble the usable storage cell (shaped recipes are also available for standard tiers).
* **Lightning Storage Cells (I–V) / Bulk Lightning Storage Cell**: Finished cells ready to place in an ME Drive to store High Voltage and Extreme High Voltage Lightning.

<RecipeFor id="ae2lt:lightning_item_cell_housing" />

<RecipeFor id="ae2lt:lightning_storage_component_i" />

## Standard Lightning Storage Cells (I–V)

Every standard cell stores both High Voltage and Extreme High Voltage Lightning. Each unit occupies 1 byte, and registered capacities account for AE2's two-type overhead:

| Cell | Effective Capacity (Both Tiers Present) | Idle Drain |
|------|----------------------------------------:|-----------:|
| Lightning Storage Cell I | 256 | 32 AE/t |
| Lightning Storage Cell II | 1,024 | 128 AE/t |
| Lightning Storage Cell III | 4,096 | 512 AE/t |
| Lightning Storage Cell IV | 16,384 | 2,048 AE/t |
| Lightning Storage Cell V | 65,536 | 8,192 AE/t |

## Bulk Lightning Storage Cell

Crafted from a **Bulk Lightning Storage Component** and a **Lightning Item Cell Housing**, this cell is built for high-volume late-game bases:

<RecipeFor id="ae2lt:bulk_lightning_storage_component" />

* **Idle Drain**: 32 AE/t.
* **Capacity**: Independent signed 64-bit counters for High Voltage and Extreme High Voltage Lightning (up to `9,223,372,036,854,775,807` each); further insertion is rejected once the cap is reached.

## Infinite Storage Cell

<ItemImage id="ae2lt:infinite_storage_cell" scale="2" float="left" />

The **Infinite Storage Cell** is a multi-type storage cell that records actually inserted resources without standard byte or type-count restrictions.

## Appendix

> Tucked into the tail of a scan log: "One cell isn't in the registry — it can only be read once and cannot be written to; otherwise everything reads normal." Source and interpretation are unrecorded.
