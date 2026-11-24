---
navigation:
  title: Pigmee Starter Automation
  icon: ae2lt:pigmee_core
  parent: materials/materials-index.md
  position: 65
item_ids:
  - ae2lt:pigmee_fumo
  - ae2lt:pigmee_core
  - ae2lt:pigmee_storage_component
  - ae2lt:pigmee_item_cell_housing
  - ae2lt:pigmee_storage_cell
  - ae2lt:pigmee_pattern_provider
  - ae2lt:pigmee_molecular_assembler
  - ae2lt:pigmee_mentalmath_unit
  - ae2lt:pigmee_synthesis_station
---

# Pigmee Starter Automation

<ItemGrid>
  <ItemIcon id="ae2lt:pigmee_fumo" />
  <ItemIcon id="ae2lt:pigmee_core" />
  <ItemIcon id="ae2lt:pigmee_storage_cell" />
  <ItemIcon id="ae2lt:pigmee_synthesis_station" />
  <ItemIcon id="ae2lt:pigmee_pattern_provider" />
  <ItemIcon id="ae2lt:pigmee_molecular_assembler" />
  <ItemIcon id="ae2lt:pigmee_mentalmath_unit" />
</ItemGrid>

The **Pigmee** line offers accessible starter storage, manual crafting, and compact autocrafting devices, connecting directly to the [Pigmee Crystal Catalyzer](../machines/crystal-catalyzer.md), [Rainbow Pigmee Dyeing](rainbow-pigmee.md), and [Pigmee Building Materials](pigmee-building-materials.md).

## Obtaining Pigmee Fumo & Pigmee Core

* **Pigmee Fumo**: By default, each player receives one **Pigmee Fumo** upon joining a world for the first time (placed in the hotbar/inventory, or dropped nearby if full). Wearing it on your head renders natural lightning strikes in pink, making natural bolts easy to spot during thunderstorms.
* **Pigmee Core**: Have an **adult Pig** stand on an **Overload Crystal Block** and take damage from a falling Anvil to transform it into 1 **Pigmee Core**. Pigmee Cores are reusable crafting ingredients across the Pigmee device recipes (check your recipe viewer for exact layouts).

## Starter Devices

| Item / Block | Capabilities & Specs |
|--------------|----------------------|
| **Pigmee Storage Component / Housing / Cell** | Combine a **Pigmee Storage Component** with a **Pigmee Item Cell Housing** to craft a **Pigmee Storage Cell** (disassembling returns the parts). Follows AE2 item cell rules: **256 bytes**, up to **16 types**, 8-byte per-type overhead, and **0.5 AE/t** idle drain. |
| **Pigmee Synthesis Station** | Features a built-in crafting grid that aggregates adjacent accessible **non-ME inventories** in real time. Manual crafting consumes **no AE**; displays a notice if no adjacent container is available. |
| **Pigmee Pattern Provider** | Holds **3 pattern slots**, dispatches to adjacent targets based on facing or all valid sides, and uses a dedicated return inventory to push results back into ME. |
| **Pigmee Molecular Assembler** | Accepts and executes compatible crafting patterns alongside a Pigmee Pattern Provider. |
| **Pigmee Mental Arithmetic Unit** | Compact crafting CPU offering **256 bytes** of crafting storage and **1** dispatch per tick; uses **1 channel** and **1 AE/t** idle power. |
| **[Pigmee Crystal Catalyzer](../machines/crystal-catalyzer.md)** | Takes a full stack of 64 catalysts and 1,000 mB Water every 100 ticks to produce the recipe's base yield with no FE or Lightning required. |
