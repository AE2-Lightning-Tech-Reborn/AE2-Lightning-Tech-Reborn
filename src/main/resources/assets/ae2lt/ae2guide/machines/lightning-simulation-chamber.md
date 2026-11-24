---
navigation:
  title: Lightning Simulation Room
  icon: ae2lt:lightning_simulation_room
  parent: machines/machines-index.md
item_ids:
  - ae2lt:lightning_simulation_room
---

# Lightning Simulation Room

<ItemImage id="ae2lt:lightning_simulation_room" scale="2" float="left" />

The **Lightning Simulation Room** is the first lightning processing machine, running shapeless counted recipes of up to 3 inputs in a controlled environment to replace manual ground lightning conversions and craft mid-to-late-game materials.

## Obtaining

* **Starter Lightning Conversion**: Drop the required ingredients on the ground and strike them with any lightning to obtain your first Lightning Simulation Room.
* **Automated Crafting**: Later, craft additional units in the [Lightning Assembly Chamber](lightning-assembly-chamber.md) (check your recipe viewer for exact ingredients and energy costs).

## Specifications & Operation

* **Inventory**: 3 input slots and 1 output slot, each holding up to **8,192** items, plus 1 matrix slot (limit 1).
* **Energy Buffer**: **20,000,000 FE**, supplied via external FE cables.
* **Upgrades**: Accepts up to 4 AE2 <ItemLink id="ae2:speed_card" />s to increase maximum FE processed per tick.
* **Locking & Completion**: Once inputs match a recipe, the machine locks it and consumes FE over time. At completion, inputs, ME Lightning stock, and output space are re-verified before deducting Lightning and producing the output.
* **Matrix Substitution**: Installing 1 <ItemLink id="ae2lt:lightning_collapse_matrix" /> allows Extreme High Voltage recipes to run entirely on High Voltage Lightning at a **4:1** ratio when EHV stock is insufficient.
* **Automation**: Configure export sides and auto-export in the UI to return products directly to adjacent containers or pattern providers. The matrix slot is hidden from standard item automation.

<OptionalSection modId="appflux">
## Network FE Refill

With Applied Flux installed, the Simulation Room automatically refills its 20,000,000 FE internal buffer from FE stored in the connected ME network.
</OptionalSection>
