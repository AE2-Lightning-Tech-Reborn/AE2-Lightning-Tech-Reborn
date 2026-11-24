---
navigation:
  title: Overload Crystal
  icon: ae2lt:overload_crystal
  parent: materials/materials-index.md
item_ids:
  - ae2lt:overload_crystal
  - ae2lt:overload_crystal_dust
  - ae2lt:overload_crystal_block
  - ae2lt:flawless_budding_overload_crystal
  - ae2lt:flawed_budding_overload_crystal
  - ae2lt:cracked_budding_overload_crystal
  - ae2lt:damaged_budding_overload_crystal
  - ae2lt:small_overload_crystal_bud
  - ae2lt:medium_overload_crystal_bud
  - ae2lt:large_overload_crystal_bud
  - ae2lt:overload_crystal_cluster
---

# Overload Crystal

<ItemGrid>
  <ItemIcon id="ae2lt:overload_crystal" />
  <ItemIcon id="ae2lt:overload_crystal_dust" />
  <ItemIcon id="ae2lt:overload_crystal_block" />
  <ItemIcon id="ae2lt:flawless_budding_overload_crystal" />
</ItemGrid>

**Overload Crystal** is the foundational material of AE2 Lightning Tech and can also summon artificial lightning on demand.

## Artificial Lightning Trigger

* **Carried**: By default, keeping an Overload Crystal in any hotbar slot or your offhand summons an artificial lightning strike at your location roughly every **200 ticks (10 seconds)**. Spectator mode is excluded, and removing the crystal from valid slots resets the timer (server config can enable main-inventory detection or disable hotbar/offhand triggers).
* **Dropped**: An Overload Crystal item entity on the ground maintains its own 200-tick timer and strikes at its position.
* **No Sky Access Required**: Neither artificial trigger checks for open sky. Sky access is only relevant for natural thunderstorm strikes on Lightning Rods.

## Budding Conversion Structures

Place an AE2 Budding Certus Quartz block in the center of a flat 3×3 layer, <ItemLink id="ae2:fluix_block" /> on the four cardinal sides, a <ItemLink id="minecraft:lightning_rod" /> directly on top of the center block, and choose corners by tier:

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/flawless_budding_overload.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

| Variant | Corner Blocks | Lightning Required | Input (Budding Certus Quartz) | Output (Budding Overload Crystal) |
|---------|---------------|--------------------|-------------------------------|-----------------------------------|
| **Refined Structure** | Overload Crystal Block ×4 | Natural only | Flawless / Flawed / Chipped / Damaged | Same tier: Flawless / Flawed / Cracked / Damaged |
| **Simple Structure** | Certus Quartz Block ×4 | Any (artificial or natural) | Flawless → Flawed; Flawed → Cracked; Chipped → Damaged | One tier lower (**does not accept Damaged Budding Certus Quartz**) |

Upon a valid strike on the Lightning Rod, the 8 outer blocks are consumed, the center transforms in place, and the Lightning Rod stays intact.

## Growth, Decay, and Harvesting

Budding Overload Crystal grows buds on adjacent air faces through four stages: Small → Medium → Large → **Overload Crystal Cluster**. All four budding tiers are accelerated by AE2 <ItemLink id="ae2:growth_accelerator" /> blocks.

* **Growth Decay**: **Flawless Budding Overload Crystal** never degrades on growth. Flawed, Cracked, and Damaged tiers each have a **1/12** chance to degrade one tier per natural growth step (Damaged degrades into an Overload Crystal Block).
* **Bud & Cluster Drops**:

| Block | Normal Harvest | Silk Touch Harvest |
|-------|----------------|--------------------|
| Small / Medium / Large Overload Crystal Bud | Overload Crystal Dust | The bud block itself |
| Overload Crystal Cluster | 4 Overload Crystals (affected by Fortune) | Overload Crystal Cluster |

* **Budding Block Drops**:

| Budding Tier | Normal Harvest | Silk Touch Harvest |
|--------------|----------------|--------------------|
| Flawless Budding Overload Crystal | Flawed Budding Overload Crystal | Flawed Budding Overload Crystal (cannot be mined as Flawless) |
| Flawed Budding Overload Crystal | Cracked Budding Overload Crystal | Flawed Budding Overload Crystal |
| Cracked Budding Overload Crystal | Damaged Budding Overload Crystal | Cracked Budding Overload Crystal |
| Damaged Budding Overload Crystal | Overload Crystal Block | Damaged Budding Overload Crystal |

> Later on, place Overload Crystal Blocks or Flawless Budding Overload Crystal inside a [Crystal Catalyzer](../machines/crystal-catalyzer.md) for high-volume crystal and dust production.
