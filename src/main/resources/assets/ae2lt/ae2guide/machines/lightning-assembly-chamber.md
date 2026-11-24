---
navigation:
  title: Lightning Assembly Chamber
  icon: ae2lt:lightning_assembly_chamber
  parent: machines/machines-index.md
item_ids:
  - ae2lt:lightning_assembly_chamber
---

# Lightning Assembly Chamber

<ItemImage id="ae2lt:lightning_assembly_chamber" scale="2" float="left" />

The **Lightning Assembly Chamber** handles complex multi-ingredient shapeless recipes across 9 input slots, crafting advanced machines, Overloaded network devices, and high-tier components.

## Specifications & Operation

* **Inventory**: 9 input slots and 1 output slot, each holding up to **8,192** items, plus 1 matrix slot (limit 1).
* **Energy Buffer**: **20,000,000 FE**.
* **Upgrades**: Accepts up to 4 AE2 <ItemLink id="ae2:speed_card" />s to increase per-tick processing speed.
* **Dedicated Recipe Set**: Runs its own assembly recipe type (separate from Simulation Room recipes), including automated crafting of the Lightning Simulation Room itself (check your recipe viewer for exact recipes).
* **Matrix Substitution**: Installing 1 <ItemLink id="ae2lt:lightning_collapse_matrix" /> lets Extreme High Voltage recipes substitute High Voltage Lightning at a **4:1** ratio when EHV is insufficient.
* **Automation**: Supports side export configuration, auto-export, and Memory Card configuration copying, sharing the same recipe locking and completion checks as the Simulation Room.

<OptionalSection modId="appflux">
## Network FE Refill

With Applied Flux installed, the Assembly Chamber automatically refills its 20,000,000 FE buffer from FE stored in the connected ME network.
</OptionalSection>
