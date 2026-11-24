---
navigation:
  title: Lightning System
  icon: ae2lt:lightning_collector
  parent: index.md
  position: 20
---

# Lightning System

AE2 Lightning Tech introduces **Lightning** as a storable ME network resource alongside items and fluids. In the world, lightning bolts are classified as natural or artificial strikes; inside the ME network, lightning is stored in two voltage tiers.

<SubPages />

## Voltage Tiers

| Lightning Resource | In-World Collection Source | Active Production | Primary Uses |
|--------------------|----------------------------|-------------------|--------------|
| **High Voltage Lightning** | Lightning Collector capturing artificial strikes | Tesla Coil (High Voltage mode, written directly into the ME network) | Standard Simulation Room, Assembly Chamber, Processing Factory, Crystal Catalyzer, and Mining Factory recipes; substitution and step-up input |
| **Extreme High Voltage Lightning** | Lightning Collector capturing natural strikes | Tesla Coil (Extreme High Voltage mode, requires a Lightning Collapse Matrix, converts 8 HV into 1 EHV) | Ultimate Overload Core, Lightning Collapse Matrix, Celestweave shields, and high-tier Railgun modes |

> Note the two separate paths: the [Lightning Collector](lightning-collector.md) captures real in-world strikes, whereas the [Tesla Coil](tesla-coil.md) manufactures Lightning directly inside the machine and inserts it into the ME network without spawning world bolts.

## Core Loop

1. **Generate or Capture**: Trigger strikes via thunderstorms or Overload Crystals and capture them with a [Lightning Collector](lightning-collector.md) topped with a Lightning Rod, or manufacture Lightning directly with a [Tesla Coil](tesla-coil.md).
2. **Store in ME**: Insert assembled [Lightning Storage Cells](lightning-storage.md) into an <ItemLink id="ae2:drive" /> so stored Lightning appears in ME terminals.
3. **Consume in Devices**: [Processing Machines](../machines/machines-index.md) and network-bound [Overload Devices](../devices-index.md) withdraw the required tier directly from ME storage.
