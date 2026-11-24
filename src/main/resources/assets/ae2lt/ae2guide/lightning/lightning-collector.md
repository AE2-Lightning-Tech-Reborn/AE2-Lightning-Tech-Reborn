---
navigation:
  title: Lightning Collector
  icon: ae2lt:lightning_collector
  parent: lightning/lightning-index.md
item_ids:
  - ae2lt:lightning_collector
  - ae2lt:electro_chime_crystal
  - ae2lt:perfect_electro_chime_crystal
---

# Lightning Collector

<ItemImage id="ae2lt:lightning_collector" scale="2" float="left" />

The **Lightning Collector** converts in-world lightning strikes into Lightning resources inside your ME network. Your first collector is crafted via in-world lightning conversion (check your recipe viewer for exact ingredients).

## Setup and Capture Rules

1. Place a **Lightning Collector** connected to an ME network with available Lightning storage.
2. Place a <ItemLink id="minecraft:lightning_rod" /> **directly on top** of the collector.
3. Whenever a lightning bolt lands within 1 block of the Lightning Rod along each axis (a 3×3×3 cube centered on the rod), the collector attempts to insert Lightning into ME storage.

Operational details:

* **Nearest-First, Single Capture**: If multiple collectors cover the same strike location, the closest Lightning Rod is tried first. As soon as one collector inserts **at least 1 unit**, the search stops for that bolt.
* **Partial Insertion**: If ME storage can only hold part of the strike's yield, the accepted portion is still inserted and counts as a successful capture.
* **Network Requirement**: The collector must belong to an ME grid with writable storage space for the captured voltage tier.
* **Capture Rate**: Each collector can complete at most one capture per tick (default cooldown 0 ticks), and its active visual state remains lit for 20 ticks.

## Crystal Slot and Electro-Chime Crystals

<ItemGrid>
  <ItemIcon id="ae2lt:electro_chime_crystal" />
  <ItemIcon id="ae2lt:perfect_electro_chime_crystal" />
</ItemGrid>

Each Lightning Collector has a crystal slot that accepts an **Electro-Chime Crystal** or **Perfect Electro-Chime Crystal** to boost output per strike:

| Crystal Slot | Artificial Strike (High Voltage) | Natural Strike (Extreme High Voltage) | Notes |
|--------------|----------------------------------|---------------------------------------|-------|
| Empty | 1–2 | 1–4 | Base output |
| Electro-Chime Crystal | Scales linearly from ~2 to 16 | Scales linearly from ~2 to 16 | Expected center scales with growth; random spread is at least ±1 (default `floor(center × 0.12)`) |
| Perfect Electro-Chime Crystal | Fixed 16 | Fixed 16 | Maximum, consistent yield |

### Obtaining and Nurturing Electro-Chime Crystals

* **Crafting**: Standard Electro-Chime Crystals can be made via in-world lightning conversion or in a Lightning Simulation Room.
* **Nurturing to Perfect**: With an Electro-Chime Crystal installed, every successful capture of **natural lightning** that writes at least 1 unit to ME storage adds 8–12 growth points, subject to a minimum 20-tick interval per collector. Reaching **180 points** automatically transforms it into a **Perfect Electro-Chime Crystal**.
* **Growth Stages**: Dormant (0%–20%, 0–35), First Chime (20%–55%, 36–98), Resonant (55%–85%, 99–152), and Overloaded (85%+, 153–179).
* **Alternative Route**: Perfect Electro-Chime Crystals can also be crafted directly in the Lightning Simulation Room at high material cost.
