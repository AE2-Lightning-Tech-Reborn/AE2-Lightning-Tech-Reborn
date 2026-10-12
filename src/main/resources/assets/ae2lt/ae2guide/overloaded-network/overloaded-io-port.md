---
navigation:
  title: Overloaded IO Port
  icon: ae2lt:overloaded_io_port
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_io_port
---

# Overloaded IO Port

<BlockImage id="ae2lt:overloaded_io_port" scale="4" />

Moves resources in bulk between storage cells and the ME network. It provides six input and six output slots and reuses the AE2 IO Port's empty, fill and eject settings, requiring a channel and network power.

## Transfer rate

Each attempt moves one resource type, and all six input cells share the attempts per round in rotation. Up to 8 **Lightning Collapse Matrices** increase both the attempt count and the amount cap:

| Matrices | Attempts per round | Per-type cap (transfer units) |
| --- | ---: | ---: |
| 0 | 4 | 8,388,608 |
| 1 | 6 | 268,435,456 |
| 2 | 8 | 8,589,934,592 |
| 3 | 10 | 274,877,906,944 |
| 4 | 12 | 8,796,093,022,208 |
| 5 | 14 | 281,474,976,710,656 |
| 6 | 16 | 9,007,199,254,740,992 |
| 7 | 16 | 288,230,376,151,711,744 |
| 8 | 16 | Long.MAX_VALUE |

Transfer units convert to native amounts using the resource type's AE2 amount per operation. Actual transfers are limited by source stock and destination capacity. `Long.MAX_VALUE` is 9,223,372,036,854,775,807.

Install matrices in the right slot below the arrow. The interval starts at 5 ticks; each acceleration card removes one tick, up to four cards.

## Power

Each extraction costs **32 AE**, independent of the amount moved, plus **4 AE/t** idle power, adjusted by the AE2 power multiplier. Transfers pause when power is insufficient.

## Controls and automation

* **Empty** sends cell contents into the network; **Fill** draws from the network into the cell.
* Eject cells when empty, when full, or when a complete scan finds no transferable resources.
* A redstone card enables ignore, high-signal and low-signal control.
* Insert storage cells through the port's local top and bottom and extract finished cells through the other four sides; rotating the block changes these directions.
* When output slots are full, cells wait in the input slots and can still be removed by hand.

## Filter component

The left slot below the arrow accepts an **Overloaded Filter Component** whose resource list is configured in a Cell Workbench; both filling and emptying then move only matching resources. A fuzzy card enables fuzzy matching and an inverter card turns the list into a blacklist.

Filtered-out resources stay in their original storage and consume no attempts or power. With a nonempty filter list, empty ejection means the cell holds no permitted resources.

## Recovery

Resources that cannot be delivered return to their source first. If the source also rejects them, the port holds them and pauses transfers. Pending resources appear in the item tooltip and survive dismantling; reconnect the port to an ME network with free space to return them.
