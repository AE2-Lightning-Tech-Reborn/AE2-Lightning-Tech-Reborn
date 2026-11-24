---
navigation:
  title: Overloaded IO Port
  icon: ae2lt:overloaded_io_port
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_io_port
---

# Overloaded IO Port

<ItemImage id="ae2lt:overloaded_io_port" scale="2" float="left" />

The **Overloaded IO Port** is a high-throughput storage cell transfer block built for rapidly emptying or filling massive item, fluid, and compatible resource cells with an ME network.

## Layout & Operating Parameters

* **Cell Slots**: 6 input slots and 6 output slots.
* **Direction & Ejection**: Supports emptying cells into the network or filling cells from the network, ejecting cells to output slots when empty, full, or no further progress is possible.
* **Automation Sides**: Top and bottom faces accept input cells; the four horizontal sides extract finished cells.
* **Upgrades & Interval**: Accepts a <ItemLink id="ae2:redstone_card" /> and up to 4 <ItemLink id="ae2:speed_card" />s. Runs once every **5 ticks** by default, minus 1 tick per Acceleration Card (down to **1 tick** at 4 cards).
* **Power**: Requires 1 channel and power (1 AE/t idle; transfer costs 1 AE per item and 8 AE per bucket of fluid, scaled by AE2 power multipliers). When power or target space is limited, it transfers only what can be paid for and stored.

## Matrix Scaling & Throughput

Insert **0–8** <ItemLink id="ae2lt:lightning_collapse_matrix" /> items to scale both attempts per cycle and per-operation transfer cap:

* **Attempts per Cycle**: `min(16, 4 + 2 × matrices)`, **shared round-robin** across the 6 input cell slots. For example, with 6 loaded cells and 0 matrices (4 attempts/cycle), one cycle services 4 cells and the next cycle continues with the remaining ones.
* **Per-Key Transfer Cap**: `8,388,608 × 32^matrices` transfer units (scaled by each resource type's per-operation unit), reaching `Long.MAX_VALUE` at **8 matrices**.

| Matrices | Attempts / Cycle | Transfer Unit Cap per Operation |
|---------:|-----------------:|--------------------------------:|
| 0 | 4 | 8,388,608 |
| 1 | 6 | 268,435,456 |
| 2 | 8 | 8,589,934,592 |
| 4 | 12 | 8,796,093,022,208 |
| 6 | 16 | 9,007,199,254,740,992 |
| 8 | 16 | `Long.MAX_VALUE` |

## Filtering & Overflow Recovery

* **Overloaded Filter Component**: Applies to both filling and emptying; supports Fuzzy Card matching and Inverter Card blacklisting. Filtered-out keys consume neither attempts nor AE transfer power.
* **Pending Recovery**: Resources extracted from a source that cannot be inserted into the destination are returned to the source first; if that also fails, they are held safely inside the port and work pauses. Breaking the block preserves the pending payload in the dropped item until placed back with available space.
