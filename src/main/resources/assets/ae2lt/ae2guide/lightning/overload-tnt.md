---
navigation:
  title: Overload TNT
  icon: ae2lt:overload_tnt
  parent: lightning/lightning-index.md
  position: 90
item_ids:
  - ae2lt:overload_tnt
---

# Overload TNT

<Row>
  <BlockImage id="ae2lt:overload_tnt" scale="4" />
</Row>

**Overload TNT** is a high-risk explosive built around an Overload Singularity and ignited with Flint and Steel, Fire Charges, or redstone. Rather than a standard explosion, it unleashes a multi-stage lightning blast.

## Crafting

<RecipeFor id="ae2lt:overload_tnt" />

## Detonation Behavior

Once primed, Overload TNT burns for an **80-tick (4-second)** fuse before scheduling a sustained lightning blast covering the initial strike, wide terrain destruction, entity lightning damage, and follow-up strikes, throttled by a server tick budget.

* **Weather Effect**: In dimensions that support weather, detonation sets thunderstorm weather for **160 ticks (~8 seconds)** before normal world timers resume.
* **Protection & Config**: Respects unbreakable special blocks and installed area-protection mods. `overloadTnt.enableTerrainDamage` in `ae2lt-common.toml` toggles standard blast effects.

> Once primed, Overload TNT cannot be recovered. Keep a wide safety margin from structures and mobs.

## Field Log: An Anomalous Explosion

> **Trial 7.** Conditions matched the previous six; however, the observed curve diverged. A [Lightning Collapse Matrix](../materials/lightning-collapse-matrix.md) was lying on the ground near the impact point. After the explosion, the immense energy inside the Overload TNT had vanished; only a few bolts and a cell not found in the records remained at the center.

> The first reproduction attempt failed. A larger sample with each variable controlled points to one condition: **the matrix has to be dropped in the world.**
