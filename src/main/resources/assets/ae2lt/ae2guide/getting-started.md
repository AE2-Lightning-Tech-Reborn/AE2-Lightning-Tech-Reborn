---
navigation:
  title: Getting Started
  icon: ae2lt:overload_crystal
  parent: index.md
  position: 10
---

# Getting Started

This page walks through the complete sequence from zero to your first working Lightning production line. AE2 Lightning Tech is designed for mid-to-late-game automation, so having a reliable ME network and steady FE generation before starting is strongly recommended.

## Prerequisites

Before beginning, make sure you have:

* A functioning ME network (an <ItemLink id="ae2:controller" /> or a small controllerless setup), an <ItemLink id="ae2:drive" />, and an <ItemLink id="ae2:inscriber" />
* A supply of <ItemLink id="ae2:certus_quartz_crystal" />, <ItemLink id="ae2:fluix_block" />, <ItemLink id="ae2:quartz_block" />, and AE2 Budding Certus Quartz
* At least one <ItemLink id="minecraft:lightning_rod" />
* A steady source of FE power for processing machines

## Natural vs. Artificial Lightning

In-world lightning strikes fall into two categories that determine conversion and collection behavior:

* **Natural Lightning**: Strikes generated naturally by thunderstorm weather. Required for the Refined Budding structure, yields **Extreme High Voltage Lightning** in a Lightning Collector, and nurtures Electro-Chime Crystals.
* **Artificial Lightning**: Strikes summoned by a Channeling trident, a carried or dropped Overload Crystal, or other mods. Valid for the Simple Budding structure and in-world item conversions, and yields **High Voltage Lightning** in a Lightning Collector.

## Step 1: Obtain Overload Crystals and Budding Blocks

<ItemImage id="ae2lt:overload_crystal" scale="2" float="left" />

**Overload Crystals** and **Overload Crystal Dust** are the entry point of the mod. They are farmed from Budding Overload Crystal blocks created by striking a 3×3 multiblock structure with lightning:

1. Build a flat 3×3 layer with a Budding Certus Quartz block in the center and <ItemLink id="ae2:fluix_block" /> on the four cardinal sides (N/S/E/W).
2. Choose the corner blocks based on your progression:
   * **Simple Structure (for starting out)**: Place <ItemLink id="ae2:quartz_block" /> on the four corners. Accepts **any lightning** (artificial or natural) and converts Flawless, Flawed, or Chipped Budding Certus Quartz into one-tier-lower Flawed, Cracked, or Damaged Budding Overload Crystal.
   * **Refined Structure (for Flawless)**: Place <ItemLink id="ae2lt:overload_crystal_block" /> on the four corners. Requires **natural lightning** and preserves the exact budding tier.
3. Place a <ItemLink id="minecraft:lightning_rod" /> directly on top of the center block and let lightning strike it. The eight surrounding blocks are consumed, the center transforms, and the Lightning Rod remains.

<GameScene zoom="4" background="transparent">
  <ImportStructure src="assets/assemblies/flawless_budding_overload.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

> Only the **Refined Structure + natural lightning** produces <ItemLink id="ae2lt:flawless_budding_overload_crystal" />, which never degrades on growth. Start with the Simple Structure first until you can craft Overload Crystal Blocks.

Budding Overload Crystal grows Small, Medium, and Large Buds into Overload Crystal Clusters on adjacent faces and responds to <ItemLink id="ae2:growth_accelerator" />. Breaking an immature bud yields Overload Crystal Dust; breaking a fully grown cluster drops Overload Crystals (affected by Fortune). See [Overload Crystal](materials/overload-crystal.md) for full drop and decay rules.

## Step 2: Early Materials via Lightning Conversion

Several starter materials and machines are crafted by dropping items on the ground and letting lightning (natural or artificial) strike them (check your recipe viewer for exact ingredients and counts in your modpack).

By default, carrying an <ItemLink id="ae2lt:overload_crystal" /> in **any hotbar slot or your offhand** summons an artificial lightning bolt at your position roughly every 200 ticks (10 seconds). An Overload Crystal dropped on the ground also runs its own independent 200-tick timer and strikes at its location. Neither artificial trigger requires direct sky access (spectator mode does not trigger, and moving the crystal out of valid slots resets the carried timer).

Typical early progression using lightning conversion and an Inscriber:

1. **Core Intermediates**: Convert your first <ItemLink id="ae2lt:overload_inscriber_press" /> and <ItemLink id="ae2lt:overload_alloy" />, press **Unoverloaded Circuit Boards** in an <ItemLink id="ae2:inscriber" />, convert them into **Overload Circuit Boards** via lightning, and finish <ItemLink id="ae2lt:overload_processor" /> in the Inscriber.
2. **Starter Machines & Storage**: Convert your first <ItemLink id="ae2lt:lightning_collector" />, <ItemLink id="ae2lt:lightning_cell_component_i" />, and <ItemLink id="ae2lt:lightning_simulation_room" />.

<OptionalSection modId="extendedae">
With ExtendedAE installed, the **Circuit Slicer** and **Crystal Assembler** offer faster shortcuts for Unoverloaded Circuit Boards and Overload Processors (check your recipe viewer for exact recipes).
</OptionalSection>

## Step 3: Assemble the Cell and Build Your First Line

Lightning conversion produces the crafting material <ItemLink id="ae2lt:lightning_cell_component_i" />, which cannot be placed in a drive directly. Combine it with a <ItemLink id="ae2lt:lightning_item_cell_housing" /> (or use the shaped recipe) to craft a <ItemLink id="ae2lt:lightning_storage_component_i" />, then insert that cell into an <ItemLink id="ae2:drive" />.

<RecipeFor id="ae2lt:lightning_storage_component_i" />

Connect your minimal **Collect → Store → Consume** loop:

1. **Collect**: Place a <ItemLink id="ae2lt:lightning_collector" /> on your ME network with a <ItemLink id="minecraft:lightning_rod" /> directly on top of it. Strikes landing within the 3×3×3 cube centered on the Lightning Rod are written straight into ME storage.
2. **Store**: Insert your **Lightning Storage Cell I** into an ME Drive. Stored High Voltage and Extreme High Voltage Lightning now appear in ME terminals alongside items and fluids.
3. **Consume**: Place a <ItemLink id="ae2lt:lightning_simulation_room" /> connected to the same ME network and supplied with FE. FE drives progress during crafting; Lightning is deducted from the ME network upon completion.

## Step 4: Next Steps

Once your starter line is running, expand in whichever direction your base needs:

* **Reliable Lightning Supply**: Build a [Tesla Coil](lightning/tesla-coil.md) to manufacture High Voltage Lightning from Overload Crystal Dust and FE, or use an [Atmospheric Ionizer](machines/atmospheric-ionizer.md) to set thunderstorm weather for natural strikes.
* **Advanced Processing**: Progress into the [Lightning Assembly Chamber](machines/lightning-assembly-chamber.md), [Overload Processing Factory](machines/overload-processing-factory.md), [Crystal Catalyzer](machines/crystal-catalyzer.md), and [Mining Factory](machines/mining-factory.md), and craft the [Lightning Collapse Matrix](materials/lightning-collapse-matrix.md) to unlock substitution and parallelism.
* **Overloaded Network**: Upgrade to the [Overloaded ME Controller](overloaded-network/overloaded-controller.md), [Overloaded Cable](overloaded-network/overloaded-cable.md), [Wireless Network](overloaded-network/wireless-network.md), 36-pattern [Overloaded Pattern Provider](overloaded-network/overloaded-pattern-provider.md), and 36-config-slot [Overloaded ME Interface](overloaded-network/overloaded-interface.md).
* **Tianshu & End-Game Gear**: Construct the [Tianshu Matter Warping Matrix](matrix/matrix-index.md) and [Tianshu Supercomputer](tianshu/tianshu-index.md), and harvest [Firmament Materials](materials/firmament-materials.md) in the End for [Celestweave](celestweave.md) armor and the [Electromagnetic Railgun](electromagnetic-railgun.md).
