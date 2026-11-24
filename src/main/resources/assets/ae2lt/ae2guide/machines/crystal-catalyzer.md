---
navigation:
  title: Crystal Catalyzer
  icon: ae2lt:crystal_catalyzer
  parent: machines/machines-index.md
item_ids:
  - ae2lt:crystal_catalyzer
  - ae2lt:pigmee_crystal_catalyzer
---

# Crystal Catalyzer & Pigmee Crystal Catalyzer

<ItemGrid>
  <ItemIcon id="ae2lt:crystal_catalyzer" />
  <ItemIcon id="ae2lt:pigmee_crystal_catalyzer" />
</ItemGrid>

The **Crystal Catalyzer** continuously duplicates crystals or crystal dust using fluid and power without consuming the catalyst. The **Pigmee Crystal Catalyzer** is a lightweight early-game variant that runs water-based recipes using only Water.

## Variant Comparison

| Property | Crystal Catalyzer (Standard) | Pigmee Crystal Catalyzer |
|----------|------------------------------|--------------------------|
| Catalyst Slot Limit | 256 | 64 (full stack of 64 required to run) |
| Output Slot Limit | 16,384 | 64 |
| Fluid Tank & Fluids | 16,000 mB; uses the recipe's fluid (usually Water, or mod-specific fluids) | Water-based recipes only; fixed 1,000 mB Water per cycle |
| Energy & Lightning | 1,000,000 FE buffer; consumes recipe FE and Lightning | No FE or Lightning required |
| Matrix Slot | 1 <ItemLink id="ae2lt:lightning_collapse_matrix" /> for **8×** output | None |
| Cycle Duration | Defined by recipe (~20 ticks min for common Crystal recipes, ~40 ticks for Dust) | Fixed 100 ticks (5 seconds) |
| Output per Cycle | Base output × parallel count × (8 if Matrix installed) | Fixed single base output |
| Upgrade Slot | 1 AE2 <ItemLink id="ae2:redstone_card" /> | None |

## Standard Crystal Catalyzer Mechanics

* **Modes**: Toggle between **Crystal** and **Dust** modes in the UI; both share the catalyst slot, fluid tank, and output slot. The catalyst is **never consumed**.
* **Parallelism & Costs**: Parallel count equals `floor(catalysts / required catalysts)`, capped by output slot capacity. Regardless of parallelism, each cycle pays only **one single share of fluid and Lightning**, while FE and duration come from the recipe. Processing pauses if output space is full.
* **Budding Block Routes**: Non-degrading budding blocks such as Flawless Budding Overload Crystal and Flawless Budding Certus Quartz yield a base output of 16 (resolved dynamically from the mature cluster loot table; datapack changes producing multiple distinct items or empty drops invalidate the recipe). On a default 20-tick Flawless Budding recipe:
  * 1 block without Matrix: **16** per cycle.
  * 1 block with Matrix: **128** per cycle.
  * 128 blocks with Matrix: **16,384** per cycle (requires continuous output extraction to sustain throughput at 20 TPS).
* **Redstone & Automation**: Supports side export configuration and auto-eject. With a Redstone Card installed, pausing preserves progress while finished outputs can still auto-export.

<OptionalSection modId="appflux">
### Network FE Refill

With Applied Flux installed, the standard Crystal Catalyzer can automatically refill its 1,000,000 FE internal buffer from FE stored in the connected ME network in addition to external FE cables.
</OptionalSection>

<OptionalSection modId="oritech">
### Oritech Fluid Recipes

With Oritech installed, specific catalyst recipes (such as Fluxite Block and Uranium Crystal) require dedicated Oritech fluids rather than Water (not supported in the Pigmee variant); check your recipe viewer for the exact fluid and amount.
</OptionalSection>

<OptionalSection modId="justdirethings">
### Just Dire Things Fluid Recipe

With Just Dire Things installed, Time Crystal catalyst recipes require a dedicated fluid rather than Water in the standard Crystal Catalyzer; check your recipe viewer for exact fluid requirements.
</OptionalSection>

## Pigmee Crystal Catalyzer Notes

Designed as an early-game bridge before a Lightning line is built:

* Requires no ME network, FE, Lightning, or Matrix.
* Place a full stack of **64** valid catalysts for a water-based recipe and supply Water; every 100 ticks it consumes 1,000 mB Water and produces one base output without consuming catalysts.
* Supports output side configuration and auto-export for early automation.
