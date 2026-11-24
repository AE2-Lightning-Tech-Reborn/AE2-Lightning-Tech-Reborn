---
navigation:
  title: Rainbow Pigmee Dyeing
  icon: ae2lt:rainbow_pigmee_fumo
  parent: materials/materials-index.md
  position: 75
item_ids:
  - ae2lt:rainbow_pigmee_fumo
  - ae2lt:dye_base
---

# Rainbow Pigmee Dyeing

<ItemGrid>
  <ItemIcon id="ae2lt:rainbow_pigmee_fumo" />
  <ItemIcon id="ae2lt:dye_base" />
</ItemGrid>

The **Rainbow Pigmee Fumo** crafts all 16 dyes, produces [Pigmee Building Materials](pigmee-building-materials.md), and recolors existing dyeable items directly. It always remains in the crafting grid after crafting.

## Single-Item Color Cycling

Place 1 Rainbow Pigmee Fumo and 1 recolorable item in any crafting grid (including the 2×2 inventory grid) to step it to the next Minecraft color:

* **16-Color Order**: White → Orange → Magenta → Light Blue → Yellow → Lime → Pink → Gray → Light Gray → Cyan → Purple → Blue → Brown → Green → Red → Black → White.
* **Colorless & Unstackable Items**: Colorless base variants (such as Glass, Terracotta, Fluix cables, or undyed Shulker Boxes) start at White. Leather armor, shields, and other unstackable items cycle one at a time (blended leather colors match the nearest standard dye first).
* **Data Preservation**: Custom names, enchantments, stored inventories, and other item components are preserved across valid color changes.

## Four-Item Direct Dyeing

Place the Rainbow Pigmee Fumo in the center of a 3×3 crafting table, look up the **Dye Base** layout for your target color, and replace the 4 **Dye Base** items with 4 identical stackable items to produce 4 directly recolored items:

* Slot positions must match the target dye recipe layout exactly (no automatic rotation or mirroring).
* All 4 input items must be **stackable and share identical item components**; use single-item cycling for unstackable gear.
* Using 4 Dye Base items still crafts 4 dyes of that color, and surrounding the Rainbow Pigmee Fumo with 8 Cobblestone crafts 8 [Pigmee Building Blocks](pigmee-building-materials.md).

## Supported Items

Covers vanilla 16-color blocks and items, Pigmee colored panels and slabs, AE2 and Overloaded cables, and modded items exposing a complete 16-color ID family. Colorless base items are detected dynamically from loaded equal-count dyeing recipes (`n items + dye → n dyed items`); ambiguous inputs that can dye into multiple distinct output families are skipped automatically.
