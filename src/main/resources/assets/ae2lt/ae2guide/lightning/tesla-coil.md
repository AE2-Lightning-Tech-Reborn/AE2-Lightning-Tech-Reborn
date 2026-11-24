---
navigation:
  title: Tesla Coil
  icon: ae2lt:tesla_coil
  parent: lightning/lightning-index.md
item_ids:
  - ae2lt:tesla_coil
---

# Tesla Coil

<ItemImage id="ae2lt:tesla_coil" scale="2" float="left" />

The **Tesla Coil** is a two-block-tall ME networked machine that actively manufactures High Voltage Lightning or steps it up into Extreme High Voltage Lightning, writing the output directly into ME storage. It operates independently of in-world strikes captured by the [Lightning Collector](lightning-collector.md).

## Operating Modes

| Mode | Cost per Operation | Duration | Output | Requirement |
|------|--------------------|---------:|--------|-------------|
| **High Voltage** | Overload Crystal Dust ×2 + 25,000 FE | 5 ticks | 1 High Voltage Lightning | Connected to an ME network with HV storage space |
| **Extreme High Voltage** | 8 High Voltage Lightning + 500,000 FE | 5 ticks | 1 Extreme High Voltage Lightning | 1 <ItemLink id="ae2lt:lightning_collapse_matrix" /> in the matrix slot and EHV storage space in ME |

Runtime behavior:

* **Batch Locking**: High Voltage mode scales its batch size to the available Overload Crystal Dust and remaining ME storage capacity at cycle start. Extreme High Voltage mode processes 8 HV into 1 EHV per cycle.
* **Energy and Completion**: Both modes run for 5 active ticks, consuming FE progressively and re-validating inputs and storage space on completion before writing straight into ME storage.

## Power Supply

The Tesla Coil holds a **16,000,000 FE** internal buffer. External FE transfer is accepted up to the requested amount and remaining buffer space without an artificial per-tick intake cap.

<OptionalSection modId="appflux">
With Applied Flux installed, the Tesla Coil can also recharge its internal buffer directly from FE stored in the connected ME network when available.
</OptionalSection>

## Redstone Control

The single upgrade slot accepts an AE2 <ItemLink id="ae2:redstone_card" />, and both the upper and lower halves read redstone signals:

* Pausing preserves the locked mode, batch size, and progress until resumed.
* When automated with an ME Level Emitter watching Lightning stock, note that batches complete as a whole, so the final stored amount may slightly overshoot the emitter threshold.
