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

## Processing and cost

Each batch takes 5 ticks and consumes blocks, tool durability, FE and one HV Lightning on completion. Processing stops when power is insufficient, the tool is unusable or the output is blocked.

By default, the factory processes 8 blocks per batch without matrices. With matrices installed, each **Lightning Collapse Matrix** provides 256 parallel operations, up to 8 matrices and 2,048 parallel operations. Loot sampling matches per-block mining in long-term yield, with more variation between batches.

Tools consume durability per block, with Fortune and Silk Touch supported, at a default cost of 256 FE per block. An **Annihilation Plane** acts as a diamond-tier tool with its enchantments, consuming no durability and a default of 1,024 FE per block.

If a tool breaks during a batch, only completed blocks are charged and unprocessed inputs remain. Unbreaking and unbreakable properties still apply.

## Slots and automation

| Slot | Capacity |
| --- | ---: |
| Block input | 4,096 |
| Tool | 1 |
| Matrix | 8 |
| Output ×9 | 4,096 each |

The internal buffer holds 4,000,000 FE and accepts FE from any side, or Applied Flux power through the ME network or a bound frequency. Pipes can insert blocks, tools and matrices and extract products.

**Auto Export** and output side settings start disabled. Enable them to send products to adjacent containers on the selected sides. A memory card copies these settings, the frequency and matrix count, taking or returning matrices from the player's inventory.

## Compatibility

Supports ordinary block items and tools with a durability tool component. Blocks with block entities (including containers) and energy-only tools are unsupported. Loot conditions that require world context may not apply; area mining and chainsaw felling have no effect.

<RecipeFor id="ae2lt:mining_factory" />
