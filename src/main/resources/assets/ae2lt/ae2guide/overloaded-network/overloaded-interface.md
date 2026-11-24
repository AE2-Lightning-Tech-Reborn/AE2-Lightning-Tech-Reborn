---
navigation:
  title: Overloaded ME Interface
  icon: ae2lt:overloaded_interface
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_interface
  - ae2lt:overloaded_filter_component
---

# Overloaded ME Interface

<ItemGrid>
  <ItemIcon id="ae2lt:overloaded_interface" />
  <ItemIcon id="ae2lt:overloaded_filter_component" />
</ItemGrid>

The **Overloaded ME Interface** provides **36 configuration slots** and supports adjacent auto-scanning, wrenched directional targeting, and same-dimension wireless face I/O (up to **1,024** bound target faces per interface).

## Output Supply & Unlimited Slot Mode

* **36 Config Slots**: Configure items or fluids and target quantities to push to connected devices.
* **Unlimited Mode**: Toggling unlimited mode on a slot removes its per-slot target quantity cap; **actual supply still draws from real ME network inventory** and remains constrained by target capacity and power (it does not create items out of thin air).
* **Normal vs. Directional**: Auto-scan automatically skips storage providers, interfaces, and pattern providers on the same network to prevent loops; wrenching a specific direction explicitly permits interacting with same-network targets on that side.
* **Wireless Mode**: Use the [Overloaded Wireless Connect Tool](overloaded-pattern-provider.md) to bind same-dimension target faces (default max distance 128 blocks) for remote supply and extraction.

## Passive Input Buffer & Persistence

Resources pushed into the interface by external machines or pipes are stored in a persistent internal buffer (merging identical keys):

* **Buffer Capacity**: Holds up to **1,024 distinct resource types**, with **36 KiB** of shared quantity space per resource category (**294,912 items** total for items).
* **Flush Cadence**: Attempts to push up to **128 types** into the ME network every **5 ticks**. If the network is full, entries wait safely in the buffer (direct GUI deposits bypass this passive buffer).
* **Break Retention**: Breaking an interface with pending buffered resources stores them inside the dropped interface item; placing it back on an active ME network resumes flushing. Memory Cards copy settings only and never duplicate buffered contents.

## Filter Component & Speed Tiers

* **Overloaded Filter Component**: Restricts active import to the component's configured filter list (configurable in a Cell Workbench).
* **Speed & Idle Power**: Higher speed tiers shorten work intervals and scale idle drain:
  * Base idle drain: **5 AE/t**.
  * Wireless overhead: **+5 AE/t** base plus **+1 AE/t** per bound connection.
  * Fast speed multiplier: **×1.5** applied to the mode's idle drain.

<OptionalSection modId="appflux">
## Induction Card Power Output (Applied Flux)

With Applied Flux installed, inserting an Induction Card allows the Overloaded ME Interface to supply ME-stored FE along its wrenched face or wireless target links.
</OptionalSection>
