---
navigation:
  title: Lightning Collapse Matrix
  icon: ae2lt:lightning_collapse_matrix
  parent: materials/materials-index.md
  position: 60
item_ids:
  - ae2lt:lightning_collapse_matrix
---

# Lightning Collapse Matrix

<ItemImage id="ae2lt:lightning_collapse_matrix" scale="2" float="left" />

The **Lightning Collapse Matrix** is a core late-game item installed into machine matrix slots to unlock lightning substitution, output multipliers, voltage step-up, or high parallelism.

`A high-strength alloy compresses and confines immense lightning into a stable form; its waveform is the inverse of volatile Overload TNT.`

> Name note: **Lightning Collapse Matrix** is this item component, distinct from the [Tianshu Matter Warping Matrix](../matrix/matrix-index.md) autocrafting multiblock.

## Obtaining

Crafted in either the [Lightning Simulation Room](../machines/lightning-simulation-chamber.md) or the [Overload Processing Factory](../machines/overload-processing-factory.md) using Extreme High Voltage Lightning and high FE power (check your recipe viewer for exact ingredients and energy costs in your pack).

## Machine & Upgrade Uses

When placed in a machine's matrix slot, the matrix is **not consumed** and remains active while installed. Upgrading a Firmament Conversion Core consumes 1 matrix permanently:

| Target | Slot Limit | Effect |
|--------|-----------:|--------|
| [Lightning Simulation Room](../machines/lightning-simulation-chamber.md) / [Lightning Assembly Chamber](../machines/lightning-assembly-chamber.md) | 1 | Substitutes High Voltage Lightning for Extreme High Voltage recipes at a **4:1** ratio when EHV is insufficient |
| [Overload Processing Factory](../machines/overload-processing-factory.md) | 32 | Covers remaining EHV deficits at a 4:1 HV ratio and grants 8 parallel operations per matrix by default (up to 256 at 32 matrices); sneak-right-click to quick-insert |
| [Crystal Catalyzer](../machines/crystal-catalyzer.md) | 1 | Multiplies output by **8×** in both Crystal and Dust modes (not applicable to the Pigmee Crystal Catalyzer) |
| [Tesla Coil](../lightning/tesla-coil.md) | 1 | Unlocks Extreme High Voltage mode to step up HV into EHV |
| [Mining Factory](../machines/mining-factory.md) | 8 | Increases batch capacity by 256 blocks per matrix (base 8 blocks/batch without a matrix) |
| [Overloaded IO Port](../overloaded-network/overloaded-io-port.md) | 8 | Scales attempts per cycle and per-operation transfer cap (reaching the signed 64-bit limit at 8 matrices) |
| [Firmament Conversion Core](firmament-materials.md) | Consumes 1 | Sneak-right-click to upgrade in place: input capacity rises from 64 to 256 and batch size increases from 1 to 16 |

## Field Notes

> A field team once filed an additional experiment report: when the matrix is **dropped in the world**, an unintended reaction can occur; the case where that reaction was ignited is logged on the [Overload TNT](../lightning/overload-tnt.md) page.

The product is an unregistered cell that can be placed into an <ItemLink id="ae2:drive" /> for decoding. It can only be read once and cannot be written to. The report says the decoded patterns are distributed extremely unevenly. After many experiments, the team compiled a special report.
