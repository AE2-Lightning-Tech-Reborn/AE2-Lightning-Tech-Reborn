---
navigation:
  title: Tianshu Crafting Terminal
  icon: ae2lt:tianshu_crafting_terminal
  parent: index.md
  position: 48
item_ids:
  - ae2lt:tianshu_crafting_terminal
  - ae2lt:wireless_tianshu_crafting_terminal
---

# Tianshu Crafting Terminal

<ItemGrid>
  <ItemIcon id="ae2lt:tianshu_crafting_terminal" />
  <ItemIcon id="ae2lt:wireless_tianshu_crafting_terminal" />
</ItemGrid>

Available in both wired and wireless variants, the **Tianshu Crafting Terminal** integrates five workstations into a single ME terminal alongside Tianshu Supercomputer inventory maintenance controls.

<RecipeFor id="ae2lt:tianshu_crafting_terminal" />

<RecipeFor id="ae2lt:wireless_tianshu_crafting_terminal" />

## Five Built-In Work Pages

Work slot contents and stonecutting selections persist when switching pages or closing the terminal (stored on the terminal part and shared by users on wired terminals, or stored on the terminal host for wireless variants):

| Work Page | Short Tab | Capabilities |
|-----------|-----------|--------------|
| **Crafting** | Craft | 3×3 crafting grid pulling from ME storage and player inventory |
| **Smithing** | Smith | Template, base, and addition slots for smithing recipes |
| **Overload Alloy Anvil** | Anvil | Repairing, enchantment combining, and renaming with vanilla XP costs and no wear |
| **Stonecutting** | Stone | Insert input, select a stonecutting recipe, and take outputs |
| **Cell Workbench** | Cell | Configure partitions, upgrade cards, fuzzy options, and copy mode on ME storage cells |

* **Clearing Slots**: Return work-grid contents to player inventory or ME storage (limited by available space).
* **Recipe Transfer & Ctrl-Craft**: Supported work pages accept recipe transfers from your recipe viewer. Holding `Ctrl` while transferring uses available stock first, opens a crafting confirmation for missing craftable ingredients, and fills the grid upon completion.

## Wireless Variant & Integrations

* Bind the Wireless Tianshu Crafting Terminal to an ME network and keep it charged; range follows your bound Wireless Access Point or [Wireless Overloaded Controller](../overloaded-network/wireless-network.md).

<OptionalSection modId="ae2wtlib">
* **AE2WTLib Universal Terminal**: With AE2WTLib installed, the Wireless Tianshu Crafting Terminal integrates with compatible Universal Wireless Terminals.
</OptionalSection>

<OptionalSection modId="jei">
## JEI Wireless Ingredient Supply

Enable **JEI Wireless Supply** under **Settings → Tianshu Settings** (off by default; independent of in-terminal Ctrl recipe transfer):

* While carrying a powered, network-bound Wireless Tianshu Terminal or compatible universal terminal in your inventory (or Curios slot), transferring a single or Shift-batch recipe via JEI into any supported crafting table or machine automatically pulls missing ingredients from your ME network into your inventory.
* Keep free inventory slots available; if a transfer is canceled or fails partway, already extracted items remain in your inventory.
</OptionalSection>

<OptionalSection modId="emi">
## EMI Recipe Transfer

With EMI installed, supported work pages inside the Tianshu Crafting Terminal support standard EMI recipe filling and `Ctrl` auto-craft filling. External workbench auto-extraction is specific to the JEI Wireless Supply feature.
</OptionalSection>

## Inventory Maintenance Access

When connected to a network with an online [Tianshu Supercomputer](../tianshu/tianshu-index.md), press `Shift + Middle Click` on any craftable entry or open the **Inventory Maintenance** overview to manage stock rules:

* Restocking starts when stock drops strictly below **Start below** and stops once it reaches **Stop at**. Each order uses the fixed batch size, may overshoot **Stop at**, and never cancels an already running order.
* Reserved Inventory rules restrict only maintenance planning—manual crafting and other ME automation remain unaffected. See [Tianshu Pattern Encoding Terminal](pattern-encoding-terminal.md#inventory-maintenance--reserved-inventory) for full details and examples.
