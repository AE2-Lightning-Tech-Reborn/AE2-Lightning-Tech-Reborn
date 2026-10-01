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

Each attempt moves one resource type, with a base budget of 1 attempt per round. The port accepts up to 16 **Lightning Collapse Matrices**; each of the first 15 adds 1 attempt for 16 attempts per round, and the 16th adds none. All six input cells share that budget in rotation, and output slots receive only finished cells.

Matrices also raise the per-attempt amount cap for a single resource type: 32,768 native units, multiplied by 8 per matrix, reaching `Long.MAX_VALUE` with the 16th matrix. The amount actually moved is further limited by source stock and destination capacity.

| Matrices | Attempts per round | Per-type amount cap |
| --- | ---: | ---: |
| 0 | 1 | 32,768 |
| 1 | 2 | 262,144 |
| 2 | 3 | 2,097,152 |
| 15 | 16 | 1,152,921,504,606,846,976 |
| 16 | 16 | Long.MAX_VALUE |

Install matrices in the right slot below the arrow and a filter component in the left slot. Acceleration cards shorten the interval independently: 5 ticks without a card, one tick less per card, and 1 tick with four cards.

## Power

Each extraction costs **32 AE**, independent of the amount moved, plus **4 AE/t** idle power. When the network cannot pay, the port waits for the next chance; a destination that rejects the resources after the simulation is still charged.

## Controls and automation

* **Empty** sends cell contents into the network; **Fill** draws from the network into the cell.
* Eject cells when empty, when full, or when a complete scan finds no transferable resources. Exhausting this tick's budget does not eject a cell early.
* A redstone card enables ignore, high-signal and low-signal control.
* Insert cells through the port's local top and bottom and extract finished cells through the other four sides; rotating the block changes these directions. Automated input accepts storage cells only.
* When output slots are full, cells wait in the input slots and can still be removed by hand.

## Filter component

The left slot below the arrow accepts an **Overloaded Filter Component** whose resource list is configured in a Cell Workbench; both filling and emptying then move only matching resources. A fuzzy card enables fuzzy matching and an inverter card turns the list into a blacklist.

Filtered-out resources consume no attempts and no power and stay in their original storage. With a component installed, empty ejection means the cell holds no permitted resources; without a component or with an empty list, the whole cell must still be empty. Swapping the component applies immediately without resetting the interval.

## Recovery

When the destination accepts less than the simulation predicted, the remainder returns to its source first; if the source rejects it too, the port holds it and pauses new work until the ME network can accept it, and the item tooltip marks such pending resources. Saving and dismantling preserve these resources, and they continue returning once the port is placed back on a network with free space. Memory cards copy settings only, while matrices are saved with the block and drop when it is dismantled.

Craft the port in the Lightning Assembly Chamber; JEI shows the recipe.
