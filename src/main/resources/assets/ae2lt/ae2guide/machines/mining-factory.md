---
navigation:
  title: Mining Factory
  icon: ae2lt:mining_factory
  parent: machines/machines-index.md
item_ids:
  - ae2lt:mining_factory
---

# Mining Factory

<ItemImage id="ae2lt:mining_factory" scale="2" float="left" />

The **Mining Factory** batch-processes block items through server-side loot table simulation using the installed tool, automating Fortune or Silk Touch drops without placing blocks in the world.

<RecipeFor id="ae2lt:mining_factory" />

## Slots, Tools & Parameters

* **Inventory**: **4,096** block input slot, **1** tool slot, **8** matrix slots, and **9 × 4,096** output slots.
* **Energy Buffer**: **4,000,000 FE**, rechargeable from any side with FE cables.
* **Cycle & Lightning**: Each batch takes **5 ticks** by default and consumes a flat **1 High Voltage Lightning** from the ME network regardless of batch size.
* **Batch Size**: Processes up to **8 blocks** per batch with no matrix. Each installed <ItemLink id="ae2lt:lightning_collapse_matrix" /> adds **256** parallel capacity (up to 8 matrices).

### Tool Options

| Tool Slot Item | Default FE / Block | Durability & Harvest Behavior |
|----------------|-------------------:|-------------------------------|
| Standard durability tool (pickaxe, shovel, axe, etc.) | 256 FE | Consumes durability normally; supports Fortune, Silk Touch, Unbreaking, and unbreakable attributes |
| AE2 <ItemLink id="ae2:annihilation_plane" /> | 1,024 FE | Acts as a diamond-tier tool with its own enchantments and **takes no durability damage** |

> Drops are evaluated against server loot tables with standard sampling variance. Modded tool abilities that depend on world block coordinates, AoE mining, or vein-mining do not run inside the factory.

## Safety & Automation

* If tool durability, input stock, or output space is limited, the factory scales down to the exact number of blocks it can safely process rather than voiding materials.
* Supports configurable output faces and auto-export. If output stalls, verify output slots, adjacent inventory space, and configured export faces.
* Side configuration, auto-export, frequency, and installed matrix count can be copied with an AE2 Memory Card.

<OptionalSection modId="appflux">
## Network FE Refill

With Applied Flux installed, the Mining Factory can also recharge its 4,000,000 FE internal buffer from FE stored in the connected ME network.
</OptionalSection>
