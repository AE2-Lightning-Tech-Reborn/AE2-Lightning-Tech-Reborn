---
navigation:
  title: Firmament Materials
  icon: ae2lt:firmament_alloy_ingot
  parent: materials/materials-index.md
item_ids:
  - ae2lt:firmament_dust
  - ae2lt:firmament_essence
  - ae2lt:firmament_mixture
  - ae2lt:firmament_alloy_ingot
  - ae2lt:firmament_superconducting_wire
  - ae2lt:inactive_firmament_spirit_core
  - ae2lt:firmament_spirit_core_oculus
  - ae2lt:firmament_spirit_core_core
  - ae2lt:firmament_spirit_core_conduit
  - ae2lt:firmament_spirit_core_stride
---

# Firmament Materials

<ItemGrid>
  <ItemIcon id="ae2lt:firmament_dust" />
  <ItemIcon id="ae2lt:firmament_essence" />
  <ItemIcon id="ae2lt:firmament_mixture" />
  <ItemIcon id="ae2lt:firmament_alloy_ingot" />
  <ItemIcon id="ae2lt:firmament_superconducting_wire" />
  <ItemIcon id="ae2lt:inactive_firmament_spirit_core" />
</ItemGrid>

**Firmament Materials** form the End-tier refining chain used to craft [Celestweave](../celestweave.md) armor and the [Electromagnetic Railgun](../electromagnetic-railgun.md).

## Step 1: Harvesting Firmament Dust in the End

1. In **the End** (`minecraft:the_end`), lay ME cables at the top buildable layer (so the host block satisfies `Y + 1 ≥ world max build height`).
2. Attach <ItemLink id="ae2:annihilation_plane" />s facing **upward** on top of the cables, connected to an active ME network with free storage.
3. Each valid plane tracks its own timer and attempts to insert 1 **Firmament Dust** into the ME network every **200 ticks (10 seconds)**, subject to power and storage availability.

> **Batch Placement**: Sneak-right-clicking the top of a cable with an ME Annihilation Plane searches connected cables on the same Y level up to 8 blocks horizontally and places up to 64 planes at once, skipping occupied faces and consuming only the items placed. Each plane still requires its own channel and power.

## Step 2: Firmament Starship & Conversion Core

The **Firmament Conversion Core** generates inside **Firmament Starship** structures in the End and operates **only within that structure's bounds**:

* **Interaction & Automation**: Right-click with ingredients to insert; right-click with an empty hand to extract outputs first. Item pipes and automation can insert and extract from any side. The core consumes **no AE, FE, or Lightning**.
* **Standard vs. Upgraded**:
  * **Standard**: Input and output slots hold 64 items each; converts **1 batch** per cycle.
  * **Upgraded**: Sneak-right-click the core with a <ItemLink id="ae2lt:lightning_collapse_matrix" /> to consume 1 matrix and upgrade it in place (preserving inventory and progress). Input capacity increases to **256** (output remains 64) and batch size scales up to **16** per cycle, bounded by available inputs and output space.

## Refining Progression

Firmament intermediates alternate between the **Firmament Conversion Core** and the [Overload Processing Factory](../machines/overload-processing-factory.md) (check your recipe viewer for exact item counts, fluids, and energy costs in your pack):

| Output | Machine | Progression Role |
|--------|---------|------------------|
| **Firmament Essence** | Firmament Conversion Core | Converted directly from Firmament Dust |
| **Firmament Mixture** | Overload Processing Factory | Processed from Firmament Dust, supporting powders, Lava, FE, and High Voltage Lightning |
| **Firmament Alloy Ingot** | Firmament Conversion Core | Refined from Overload Alloy, Firmament Mixture, and Netherite Scrap |
| **Firmament Superconducting Wire** | Overload Processing Factory | Processed from Overload Crystal Dust, Firmament Essence, supporting materials, FE, and High Voltage Lightning |
| **All Four Firmament Spirit Cores** | Firmament Conversion Core | Activates an Inactive Firmament Spirit Core into all four piece cores |

## Inactive & Active Spirit Cores

<ItemGrid>
  <ItemIcon id="ae2lt:firmament_spirit_core_oculus" />
  <ItemIcon id="ae2lt:firmament_spirit_core_core" />
  <ItemIcon id="ae2lt:firmament_spirit_core_conduit" />
  <ItemIcon id="ae2lt:firmament_spirit_core_stride" />
</ItemGrid>

* **Sources of Inactive Spirit Core**: Naturally generated Firmament Conversion Cores have a **50%** chance to start pre-loaded with 1 Inactive Firmament Spirit Core; End City treasure chests also have a **15%** chance to contain one.
* **Activation**: Processing 1 Inactive Firmament Spirit Core in a Firmament Conversion Core (200 ticks) yields all four active Spirit Cores, corresponding to the four Celestweave armor pieces:
  * **Firmament Spirit Core: Oculus** → Celestweave Oculus (Helmet)
  * **Firmament Spirit Core: Core** → Celestweave Core (Chestplate)
  * **Firmament Spirit Core: Conduit** → Celestweave Conduit (Leggings)
  * **Firmament Spirit Core: Stride** → Celestweave Stride (Boots)
