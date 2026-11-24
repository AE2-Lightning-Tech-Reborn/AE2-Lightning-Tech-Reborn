---
navigation:
  title: Overload Machine Frame & Structural Blocks
  icon: ae2lt:overload_machine_frame
  parent: materials/materials-index.md
item_ids:
  - ae2lt:overload_machine_frame
  - ae2lt:overload_alloy_anvil
---

# Overload Machine Frame & Structural Blocks

<ItemGrid>
  <ItemIcon id="ae2lt:overload_machine_frame" />
  <ItemIcon id="ae2lt:overload_alloy_anvil" />
</ItemGrid>

## Overload Machine Frame

The **Overload Machine Frame** is a shared structural crafting component used in many processing machines. Because individual machines may be crafted on a crafting table or in a Lightning Assembly Chamber, check the specific machine page or your recipe viewer for exact recipes.

<RecipeFor id="ae2lt:overload_machine_frame" />

## Overload Alloy Anvil

The **Overload Alloy Anvil** is crafted from a vanilla Anvil and Overload Alloy. It preserves standard anvil mechanics for renaming, repairing, experience cost, and enchantment combining, and still obeys gravity when unsupported, but **takes no durability damage during normal use**. The [Tianshu Crafting Terminal](../terminals/crafting-terminal.md) also provides a built-in Overload Alloy Anvil work page.

<RecipeFor id="ae2lt:overload_alloy_anvil" />

## Block of Silicon

Recipes such as bulk Overload Processor manufacturing match silicon blocks through the `c:storage_blocks/silicon` tag:

<OptionalSection modId="!extendedae">
ExtendedAE is not installed, so AE2 Lightning Tech registers its own Block of Silicon (`ae2lt:silicon_block`), crafted from and unpacked into 9 Silicon.

<RecipeFor id="ae2lt:silicon_block" />
</OptionalSection>

<OptionalSection modId="extendedae">
ExtendedAE is installed, so AE2 Lightning Tech omits its duplicate Block of Silicon and recipes use ExtendedAE's Block of Silicon directly.
</OptionalSection>
