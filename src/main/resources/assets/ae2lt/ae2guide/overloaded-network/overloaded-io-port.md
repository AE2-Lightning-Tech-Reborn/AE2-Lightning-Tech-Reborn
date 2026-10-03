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

Crafting requires 1 **Ultimate Overload Core**, placing this port at the overload-core stage.

## Transfer rate

Each attempt moves one resource type, with a base budget of 4 attempts per round. The port accepts up to 8 **Lightning Collapse Matrices**; each adds 2 attempts, capped at 16 attempts per round with 6 matrices. The seventh and eighth matrices continue raising the amount cap. All six input cells share that budget in rotation, and output slots receive only finished cells. Rejected or missing resources also spend an attempt.

Matrices also raise the per-attempt amount cap for a single resource type: 8,388,608 transfer units, multiplied by 32 per matrix, reaching `Long.MAX_VALUE` (9,223,372,036,854,775,807) with the 8th matrix. All registered resource types use their AE2 amount per operation to convert transfer units into native amounts. Converted amounts also saturate at `Long.MAX_VALUE`; resource types with more than one native unit per operation may reach that limit earlier. The amount actually moved is further limited by source stock and destination capacity.

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

Install matrices in the right slot below the arrow and a filter component in the left slot. Acceleration cards shorten the interval independently: 5 ticks without a card, one tick less per card, and 1 tick with four cards.

At 20 TPS, continuous transfers of one resource type have a theoretical base rate of 33,554,432 transfer units/s without matrices or acceleration cards, and 167,772,160 units/s with four cards. These are 25.6x and 128x the fully accelerated ExtendedAE IO Port's 1,310,720 units/s, respectively. Mixed-resource rates also depend on scanning and available storage. The status tooltip shows the per-attempt cap in transfer units.

## Power

Each extraction costs **32 AE**, independent of the amount moved, plus **4 AE/t** idle power. When the network cannot pay, the port waits for the next chance; a destination that rejects the resources after the simulation is still charged.

With six or more matrices and four acceleration cards, sixteen paid batches per tick plus idle power cost at most **516 AE/t** before the configured AE2 power multiplier.

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
