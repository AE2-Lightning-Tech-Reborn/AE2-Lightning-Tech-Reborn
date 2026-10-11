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

The <ItemLink id="ae2lt:tianshu_crafting_terminal" /> combines a crafting table, smithing table, Overload Alloy Anvil, stonecutter, and Cell Workbench in one ME terminal. Attach it to a cable; **no Tianshu Supercomputer is required**.

<RecipeFor id="ae2lt:tianshu_crafting_terminal" />

The wireless version offers the same work pages and can also be used as a Wireless Universal Terminal module.

<RecipeFor id="ae2lt:wireless_tianshu_crafting_terminal" />

<a name="work-pages"></a>

## Work Pages

Use the tabs on the right to switch pages:

* **Crafting:** Make items in the 3×3 crafting grid.
* **Smithing:** Insert a template, base, and addition, then take the result.
* **Anvil:** Repair, combine enchantments, or rename items. Taking the result costs the displayed experience.
* **Stonecutting:** Insert an ingredient, select a recipe, and take the result.
* **Cell Workbench:** Insert an editable ME storage cell to configure partitions, upgrade cards, and fuzzy matching. Scroll the filter and upgrade areas separately; copy mode controls whether the configuration remains after removing the cell.

Crafting, smithing, stonecutting, and anvil pages support JEI recipe transfer. **Ctrl-click transfer** uses available ingredients first, then opens crafting confirmation for missing craftable materials. Transfer again after the job completes.

## Saved Inputs

Switching pages or closing the terminal keeps its inputs. Smithing, anvil, stonecutting, and Cell Workbench inputs and settings stay with the terminal, so you can continue when reopening it. They do not return to your inventory when closing. All players using the same wired terminal share its work slots.

<a name="inventory-maintenance"></a>

## Inventory Maintenance

When a formed Tianshu Supercomputer is available on the network, the terminal can configure automatic restock rules per item: crafting starts when the stored amount drops **below** the lower bound, stops when it **reaches** the upper bound, and each job requests the configured batch size.

### Configuration

* **Shift + middle-click** a craftable item in the terminal to open its Inventory Maintenance editor, which works much like an ME Requester.
* The **Inventory Maintenance** button opens an overview listing every configured entry, including materials whose current stock is zero. Click an entry to edit its rule.
* The **Maintainable** view filters the terminal list to materials configured for inventory maintenance, making them easier to review together.

* **Start below:** Starts automatic crafting when the material falls below the configured value.
* **Stop above:** Stops automatic crafting when the material rises above the configured value.
* **Per job:** Sets the amount requested by each automatic crafting job.
* **Enabled:** Enables or disables this material's maintenance rule.
* **Check now:** Immediately checks this material's stock.
* **Cancel job:** Cancels the currently running automatic crafting job.
* **Crafting topology:** Configures material retention so that specified materials remain in stock.

Click any entry under **Crafting topology** to set how much of that material maintenance jobs may not consume. Switch **Tianshu global** to **Rule additional** to configure an extra reserve for this rule. A rule-specific reserve only changes the effective protection when it is greater than the matching global reserve; see “Global Reserves” below.

> Configured materials show a colored marker in Tianshu terminals: gray means the rule is disabled, green means the stock target is satisfied, orange means the rule is idle or crafting, and red indicates missing materials.

<a name="global-reserves"></a>

## Global Reserves

A global reserve protects a quantity of an item from automatic maintenance jobs. Player-requested crafting jobs may still use this protected stock.
Use the search box to find stored network content and click an item to configure it. Items may also be dragged from JEI or EMI onto the target in the lower-left corner.
**Exact match** protects only the selected component variant, including properties such as durability and enchantments. **Ignore components** groups all variants with the same item ID.
**Reserve** sets the protected quantity. Set it to `-1` to reserve all existing stock.

If the overview becomes a recovery page, zero out or delete old entries one by one to reveal the remaining entries.

<a name="settings"></a>

## Terminal Settings

Open **Terminal Settings → Tianshu Settings** to enable **JEI wireless ingredients**. Wired and wireless terminals share this setting.

This feature is off by default. Carry a powered, connected wireless Tianshu terminal in your inventory or Curios. When transferring a recipe to a workbench or machine with JEI transfer support, missing items are supplied from the network. Tianshu modules in a Wireless Universal Terminal work too.

Keep inventory space free for ingredients. A normal click fills one recipe; Shift fills in bulk. Only items are supplied. If transfer fails or you close the screen, extracted items remain in your inventory.
