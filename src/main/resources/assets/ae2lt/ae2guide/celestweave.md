---
navigation:
  title: Celestweave
  icon: ae2lt:celestweave_core
  parent: devices-index.md
  position: 20
item_ids:
  - ae2lt:celestweave_oculus
  - ae2lt:celestweave_core
  - ae2lt:celestweave_conduit
  - ae2lt:celestweave_stride
  - ae2lt:module_night_vision
  - ae2lt:module_water_breathing
  - ae2lt:module_saturation
  - ae2lt:module_reach_extension
  - ae2lt:module_matrix_shield
  - ae2lt:module_phase_shield
  - ae2lt:module_reflect
  - ae2lt:module_overload_protection
  - ae2lt:module_multidimensional_protection
  - ae2lt:module_purification
  - ae2lt:module_radiation_protection
  - ae2lt:module_laser_protection
  - ae2lt:module_creative_flight
  - ae2lt:module_phase_flight
  - ae2lt:module_phase_lock
  - ae2lt:phase_lock_projection
  - ae2lt:module_dash
  - ae2lt:module_dig_affinity
  - ae2lt:module_movement_assist
---

# Celestweave

<ItemGrid>
  <ItemIcon id="ae2lt:celestweave_oculus" />
  <ItemIcon id="ae2lt:celestweave_core" />
  <ItemIcon id="ae2lt:celestweave_conduit" />
  <ItemIcon id="ae2lt:celestweave_stride" />
</ItemGrid>

**Celestweave** is a four-piece modular armor set consisting of **Celestweave Oculus** (Helmet), **Celestweave Core** (Chestplate), **Celestweave Conduit** (Leggings), and **Celestweave Stride** (Boots). Install an Ultimate Overload Core and modules into each piece at the [Overload Device Workbench](devices-index.md), then configure them in the **Overload Device** menu (<KeyBind id="key.ae2lt.open_config" />).

## Energy Capacity

Each piece starts with a **10,000,000 FE** internal buffer and accepts 1 Energy Module:

| Energy Module | FE Capacity per Piece |
|---------------|----------------------:|
| None | 10,000,000 FE |
| Energy Module T1 | 1,000,000,000 FE |
| Energy Module T2 | 5,000,000,000 FE |
| Energy Module T3 | 20,000,000,000 FE |

## Modules by Piece

| Piece | Available Modules |
|-------|-------------------|
| **Celestweave Oculus** (Helmet) | **Night Vision Module**, **Water Breathing Module**, **Saturation Module** (periodically restores hunger and saturation) |
| **Celestweave Core** (Chestplate) | **Reach Extension Module**, **Protection Group (choose 1)**: Matrix Shield / Phase Shield / Overload Protection / Multidimensional Protection, **Reflect Module** (reflects up to **30%** of incoming damage to eligible living attackers), **Purification Module**, **Phase Lock Module**, **Overload Core Module** (shared with Railgun; enables 16:1 HV substitution) |
| **Celestweave Conduit** (Leggings) | **Flight Group (choose 1)**: Creative Flight Module / Phase Flight Module — double-tap Space mid-air to hover, hold Space while gliding to boost, Space/`Shift` to ascend/descend while hovering; Phase Flight adds **Phase Mode** (`Off`, `Creative Flight Only`, `Creative + Elytra`) |
| **Celestweave Stride** (Boots) | **Dash Module** (hotkey: <KeyBind id="key.ae2lt.dash" />, default `V`), **Dig Affinity Module** (underwater & airborne mining speed), **Movement Assist Module** (walk/sprint/sneak speed & step assist) |

<OptionalSection modId="mekanism">
### Mekanism Protection Modules (Chestplate)

With Mekanism installed, two dedicated chestplate modules are supported:

* **Radiation Assimilation Module**: Mitigates Mekanism radiation.
* **Laser Protection Module**: Protects against Mekanism laser damage.
</OptionalSection>

## Shield Costs, Undying & Substitution

* **Phase Shield**: Covers up to **1,024 damage** per hit at **20,000 FE + 2 Extreme High Voltage Lightning** per point.
* **Overload Protection**: Shield absorption and death prevention share a fixed **20-tick** billing window:
  * Standard shielding only pays the incremental difference up to the window's highest hit, capped at **2 GFE** and **1,024 EHV** per window.
  * Death prevention scales with triggers within the window, up to **20 GFE** and **16,384 EHV**.
* **Multidimensional Protection**: Requires no shield or death-prevention billing cost.
* **Overload Core Substitution**: Installing an **Overload Core Module** in the chestplate draws existing EHV first, then covers any missing EHV at **16 High Voltage : 1 Extreme High Voltage**.

## Phase Lock Module

Installed on the chestplate, **Phase Lock** exposes four independent controls in the **Overload Device** menu:

* **Armor Lock**: Stores your true armor in a protected private slot while displaying **Phase-Locked Projections** in the armor slots. Tampered projections are rebuilt at resource cost; if resources run out, the lock disengages and restores the real armor.
* **Flight Lock**: Preserves permitted flight states against external interruption.
* **Block External Forces**: Resists external pulls and forced displacement.
* **Block External Teleports**: Blocks external forced teleports by source.

## Player-Owned Entities

Living entities directly owned by a player can wear Celestweave armor and run supported modules:

* The server ticks armor power, Lightning billing, and supported passive/defensive modules directly on the entity—even while the owner is offline.
* Look directly at a worn owned entity within reach and press **Open Overload Device Config** (<KeyBind id="key.ae2lt.open_config" />) to configure its equipment.
* Only entity-compatible options are shown; player-input features remain exclusive to player wearers.
