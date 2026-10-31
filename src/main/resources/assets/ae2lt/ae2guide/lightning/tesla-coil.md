---
navigation:
  title: Tesla Coil
  icon: ae2lt:tesla_coil
  parent: lightning/lightning-index.md
item_ids:
  - ae2lt:tesla_coil
---

# Tesla Coil

<Row>
  <BlockImage id="ae2lt:tesla_coil" scale="4" />
</Row>

The **Tesla Coil** is the core device for controlled, stable Lightning production. It does not depend on the weather — it produces Lightning on demand by consuming FE and materials.

## Two Operating Modes

The Tesla Coil GUI lets you switch between two modes.

### High Voltage Mode

* **Input**: Overload Crystal Dust + FE
* **Output**: High Voltage Lightning (injected into the ME network)
* **Default cost**: 2 Overload Crystal Dust + 25,000 FE per 1 HV Lightning produced

High Voltage mode supports batch processing. The coil locks in the full amount of available crystal dust in its input slot and the HV Lightning headroom left in the ME network, then produces that entire batch in one charge cycle.

### Extreme High Voltage Mode

* **Input**: High Voltage Lightning (pulled from the ME network) + FE, and a **Lightning Collapse Matrix** must be installed
* **Output**: Extreme High Voltage Lightning (injected into the ME network)
* **Default cost**: 8 HV Lightning + 500,000 FE per 1 EHV Lightning produced
* Each charge cycle produces exactly 1 EHV Lightning. The matrix is not consumed.

## Operating Flow

1. Select a mode from the GUI
2. Prepare materials — HV mode: put Overload Crystal Dust into the slot; EHV mode: verify the matrix is installed and the network has enough HV Lightning
3. Supply FE (the coil has a 16,000,000 FE internal buffer and accepts up to 200,000 FE/tick)
4. The coil locks in the mode and starts charging (about 5 ticks)
5. When the charge completes, the output is injected into the ME network in one batch

## Lightning Collapse Matrix

<ItemImage id="ae2lt:lightning_collapse_matrix" scale="2" float="left" />

Extreme High Voltage mode requires a **Lightning Collapse Matrix** in the coil's matrix slot. The matrix is **not consumed** during processing, but it must stay in the slot.

## Redstone Control

The upgrade panel accepts one AE2 Redstone Card. With the card installed, the toolbar button cycles through **Ignore Redstone**, **Active with Signal**, and **Active without Signal**. Without a card, automatic operation is unchanged. Pulse mode is not supported.

- To limit output stock, configure an ME Level Emitter to signal when the output reaches its stop condition, and select Active without Signal on the machine.
- To preserve input stock, monitor the consumed resource, signal while enough remains, and select Active with Signal.

A redstone stop preserves the current recipe and paid progress, pausing processing energy, material consumption and new output. Processing resumes when allowed. Automation can still remove existing items. The emitter measures only its own ME network inventory; export catalyzer products into that network for output-stock control.

This is a signal gate: one output batch, one input debit or concurrent machines can cross a threshold. Leave room for a full operation when protecting an input reserve, and reduce batch size when a tighter output limit is needed.

Either half of the Tesla Coil can receive the control signal.
