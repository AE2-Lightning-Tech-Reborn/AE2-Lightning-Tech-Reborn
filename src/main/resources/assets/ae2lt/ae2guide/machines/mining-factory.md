---
navigation:
  title: Mining Factory
  icon: ae2lt:mining_factory
  parent: machines/machines-index.md
item_ids:
  - ae2lt:mining_factory
---

# Mining Factory

<BlockImage id="ae2lt:mining_factory" scale="4" />

The **Mining Factory** produces mining drops from each block's own loot table without placing or breaking blocks. Insert block items with a durability tool or an AE2 Annihilation Plane, then connect an ME network holding HV Lightning and supply FE.

Crafting requires 1 **Ultimate Overload Core**, placing this machine at the overload-core stage.

## Processing and cost

Every batch takes 5 ticks. Blocks, tool durability, FE and one HV Lightning are charged only on the fifth tick; progress is cleared when FE or lightning runs out, the tool tier is too low, the input is unsupported, the output is blocked or the tool breaks, while refilling or extracting products does not interrupt it.

Without a matrix the factory processes 1 block per batch, each **Lightning Collapse Matrix** adds 8 parallel operations, and the matrix slot holds up to 32 (256 parallel). Loot is sampled 8 times per batch by default, matching per-block mining in long-term yield with more variation; set `miningFactory.lootSamplesPerTick` at or above the parallel capacity for independent per-block rolls.

Tools consume their tool-component durability per block, Fortune and Silk Touch apply through the loot table, and the default cost is 256 FE per block. An **Annihilation Plane** selects a diamond-tier tool by AE2's rules and carries its enchantments, costing no durability but 768 extra FE per block.

## Slots and automation

| Slot | Capacity |
| --- | ---: |
| Block input | 4,096 |
| Tool | 1 |
| Matrix | 32 |
| Output ×9 | 4,096 each |

The internal buffer holds 1,000,000 FE and accepts FE from any side, or Applied Flux power through the ME network or a bound frequency. Each batch consumes one HV Lightning from the network, independent of parallel capacity. Pipes can insert blocks, tools and matrices and extract products; automated extraction and automatic export only handle products.

The toolbar's **Auto Export** switch and output side settings start disabled and send products to adjacent containers relative to the machine's orientation. A memory card copies the export switch, output faces, frequency and desired matrix count, taking or returning the exact matrices from the player's inventory.

## Compatibility

Ordinary block items and tools with a durability tool component are supported; blocks with block entities (containers included) and energy-only tools are not. Modded loot conditions that need world context may not apply, and area mining or chainsaw felling are not simulated.

<RecipeFor id="ae2lt:mining_factory" />
