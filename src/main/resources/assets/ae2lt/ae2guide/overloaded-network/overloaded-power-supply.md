---
navigation:
  title: Overloaded Power Supply
  icon: ae2lt:overloaded_power_supply
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_power_supply
---

# Overloaded Power Supply

<OptionalSection modId="!appflux">
The **Overloaded Power Supply** is only registered when **Applied Flux** is installed. Because Applied Flux is not currently loaded, this block is unavailable in your instance.
</OptionalSection>

<OptionalSection modId="appflux">
<ItemImage id="ae2lt:overloaded_power_supply" scale="2" float="left" />

The **Overloaded Power Supply** extracts FE from Applied Flux ME storage and distributes it wirelessly to same-dimension machines linked with the [Overloaded Wireless Connect Tool](overloaded-pattern-provider.md).

> Choosing between wireless FE methods: the **Overloaded Power Supply** binds up to **64** same-dimension target faces (default 128-block max range) for concentrated machine clusters, whereas the [Overloaded Frequency Card](overloaded-frequency-card.md) provides cross-dimensional wireless FE via an Advanced Wireless Overloaded Controller.

## Operating Modes

| Mode | Energy Cell Requirement | Transfer Behavior & Cost |
|------|-------------------------|--------------------------|
| **Normal Mode** | No energy cell required | Continuously distributes FE through the shared scheduler at standard 1:1 FE cost |
| **Overload Mode** | Requires an energy cell buffer | Runs an initial probe and then performs up to **64** consecutive push calls per tick per active target during active output phases, at **2×** FE cost |

## Usage Notes

1. Connect the Overloaded Power Supply to an ME network with stored Applied Flux FE.
2. Right-click the power supply with an **Overloaded Wireless Connect Tool**, then right-click the FE-accepting face of each target machine (up to 64 faces; Ctrl + Right-Click batch-links connected identical machines).
3. Actual transfer rate depends on each target's per-call acceptance cap, network FE availability, and buffer capacity in Overload mode. Ensure your FE reserve covers the 2× cost before enabling Overload mode.
</OptionalSection>
