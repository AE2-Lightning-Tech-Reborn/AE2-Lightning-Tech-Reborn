---
navigation:
  title: Electromagnetic Railgun
  icon: ae2lt:electromagnetic_railgun
  parent: devices-index.md
  position: 10
item_ids:
  - ae2lt:electromagnetic_railgun
  - ae2lt:railgun_module_core
  - ae2lt:railgun_module_compute
  - ae2lt:railgun_module_acceleration
  - ae2lt:railgun_module_ehv_beam
  - ae2lt:railgun_module_range
  - ae2lt:railgun_module_overload_execution
  - ae2lt:railgun_module_multidimensional_execution
  - ae2lt:energy_module_t1
  - ae2lt:energy_module_t2
  - ae2lt:energy_module_t3
---

# Electromagnetic Railgun

<ItemImage id="ae2lt:electromagnetic_railgun" scale="2" float="left" />

The **Electromagnetic Railgun** is a network-bound modular weapon offering continuous beam fire and three-tier charged shots. Bind it to an ME network and install an <ItemLink id="ae2lt:ultimate_overload_core" /> in the [Overload Device Workbench](devices-index.md) before firing.

## Core Requirements

* **Ultimate Overload Core (Base Requirement)**: Required to operate the weapon and fire the **standard continuous beam** (hold Attack in mainhand).
* **Overload Core Module (Advanced Requirement)**: Unlocks **charged shots** (hold Use) and enables **16 High Voltage : 1 Extreme High Voltage** substitution (spending existing EHV first).

## Firing Modes & Default Values

> Values below are base defaults; actual combat output depends on installed modules, server configuration, and target defenses. Base range is **64 blocks**.

### Continuous Beam (Hold Attack)

| Beam Mode | Default Per Pulse (Every 2 Ticks) | Lightning Cost | Requirement |
|-----------|----------------------------------:|----------------|-------------|
| **Standard Beam** | 20 base damage · 400 FE | 1 High Voltage every 8 pulses | Ultimate Overload Core installed |
| **EHV Beam** | 200 base damage · 4,000 FE | 16 Extreme High Voltage per pulse | **Extreme High Voltage Beam Module** installed and toggled on in **Overload Device** (uses Overload Core substitution if EHV is low; halts if resources run out) |

### Charged Shots (Hold Use, Release to Fire)

Charging draws continuous FE while holding. Impacts include splash damage, recoil, thunderstorm bonuses, and electromagnetic stun (**wearing a full Celestweave armor set eliminates charged-shot recoil**):

| Charge Tier | Base Charge Time | Default Base Damage | Fire Cost |
|-------------|-----------------:|--------------------:|-----------|
| **EHV1** | 10 ticks | 100 | 8,000 FE + 32 EHV Lightning |
| **EHV2** | 24 ticks | 300 | 40,000 FE + 96 EHV Lightning |
| **EHV3** | 40 ticks | 600 | 200,000 FE + 256 EHV Lightning |

## Energy Capacity & Modules

Base capacity is **1,000,000 FE**, expanding to **100,000,000**, **500,000,000**, or **2,000,000,000 FE** with Energy Module T1, T2, or T3.

| Module | Max | Effect |
|--------|----:|--------|
| **Overload Core Module** | 1 | Unlocks charged shots and 16:1 EHV substitution |
| **Extreme High Voltage Beam Module** | 1 | Enables EHV Beam toggle in the **Overload Device** menu |
| **Overload Compute Module** | 2 | Enhances chaining and targeting behavior |
| **Overload Acceleration Module** | 2 | Reduces charge duration |
| **Overload Range Module** | 2 | Extends effective range beyond the 64-block base |
| **Overload Execution / Multidimensional Execution Module** | Exclusive (1 of 2) | Select execution mode in **Overload Device**: **Overload Execution** adds **5% / 10% / 20%** max-HP bonus damage across EHV1/EHV2/EHV3 (subject to standard damage resolution and extra FE); **Multidimensional Execution** has no percentage tiers, upgrades execution behavior, and preserves original firing costs |

## Configuration & Safety Toggles

Damage multipliers, PvP rules, terrain destruction, and chaining limits are governed jointly by **server configuration** and your local switches inside **Overload Device** (<KeyBind id="key.ae2lt.open_config" />). Beams, charged impacts, chaining, and execution each evaluate their own range and cost rules independently.
