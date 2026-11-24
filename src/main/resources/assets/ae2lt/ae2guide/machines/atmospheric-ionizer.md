---
navigation:
  title: Atmospheric Ionizer
  icon: ae2lt:atmospheric_ionizer
  parent: machines/machines-index.md
item_ids:
  - ae2lt:atmospheric_ionizer
  - ae2lt:clear_condensate
  - ae2lt:rain_condensate
  - ae2lt:thunderstorm_condensate
---

# Atmospheric Ionizer

<ItemImage id="ae2lt:atmospheric_ionizer" scale="2" float="left" />

The **Atmospheric Ionizer** spends AE power directly from the ME network together with a **Weather Condensate** to change the current dimension's weather.

## Condensates & Costs

All three Weather Condensates are crafted on a crafting table:

<ItemGrid>
  <ItemIcon id="ae2lt:clear_condensate" />
  <ItemIcon id="ae2lt:rain_condensate" />
  <ItemIcon id="ae2lt:thunderstorm_condensate" />
</ItemGrid>

| Condensate | Target Weather | Total AE Cost | Resulting Weather Duration (ticks) |
|------------|----------------|--------------:|-----------------------------------:|
| **Clear Condensate** | Clear | 500,000 AE | 12,000–180,000 |
| **Rain Condensate** | Rain | 1,000,000 AE | 12,000–24,000 |
| **Thunderstorm Condensate** | Thunderstorm | 8,000,000 AE | 3,600–15,600 |

## Operating Rules

* **Power Model**: Draws **AE power** directly from the ME network; requires no FE buffer and no stored Lightning, and has no upgrade slots.
* **Cycle**: Spreads the AE cost across **100 powered ticks** and consumes 1 condensate only when the weather change succeeds.
* **Dimension & State Checks**: Does not operate in dimensions without weather (such as the Nether or the End), and waits without consuming a condensate if the world is already in the target weather state.
* **Natural Strikes**: Triggering a thunderstorm establishes the weather required for natural lightning; actual strike timing and locations remain governed by Minecraft's weather system independently of the ionizer's 100-tick cycle.
