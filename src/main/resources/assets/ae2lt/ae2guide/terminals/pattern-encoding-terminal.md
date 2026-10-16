---
navigation:
  title: Tianshu Pattern Encoding Terminal
  icon: ae2lt:tianshu_pattern_encoding_terminal
  parent: index.md
  position: 47
item_ids:
  - ae2lt:tianshu_pattern_encoding_terminal
  - ae2lt:wireless_tianshu_pattern_encoding_terminal
  - ae2lt:closed_loop_pattern
---

# Tianshu Pattern Encoding Terminal

The <ItemLink id="ae2lt:tianshu_pattern_encoding_terminal" /> provides everything the normal Pattern Encoding Terminal does, plus enhanced processing-pattern encoding, pattern upload, closed-loop pattern authoring, and inventory-maintenance configuration. Attach it to ME cable like any terminal. **Pattern encoding and uploading to providers require no Tianshu Supercomputer**.

<RecipeFor id="ae2lt:tianshu_pattern_encoding_terminal" />

The wireless version provides the same encoding features and can also be used as a Wireless Universal Terminal module.

<RecipeFor id="ae2lt:wireless_tianshu_pattern_encoding_terminal" />

## Encoding Modes

The terminal offers crafting, processing, stonecutting, smithing, and **Closed-Loop Pattern** modes.

## Binding to a Tianshu

Only two features require a formed Tianshu Supercomputer: uploading closed-loop patterns into Closed-Loop Pattern Storage, and inventory maintenance. When a Tianshu exists on the network, the opened terminal locks onto the first available formed one; if none is available yet, it locks onto the first Tianshu that comes online.

The terminal never rebinds to a different Tianshu while it stays open. If the locked Tianshu goes offline or unforms, the related actions simply fail; close and reopen the terminal to pick a target again.

The Closed-Loop Pattern Storage installed in the bound Tianshu's cooling-compatible positions appears as an extra pattern inventory in the terminal's network-content list. The entry is shown only while the structure is formed and its port is online. Each Closed-Loop Pattern Storage provides **36** closed-loop pattern slots, and installed storages add up. Closed-loop patterns can be taken out of the list for inspection or editing; returning them to the storage requires the terminal's upload function.

<a name="processing-patterns"></a>

## Enhanced Processing Patterns

In processing mode the terminal adds:

* **Multiplier buttons:** Multiply or divide all input and output amounts by 2 or 5; hold Shift for 4 or 10 to rescale a recipe into whole batches.
* **Advanced encoding** (requires AdvancedAE): Configure an insertion side for each input; once configured, encoding produces an advanced pattern.
* **Overload encoding:** Choose a match mode for each input and output — “Strict” requires identical item NBT data, “ID only” ignores NBT differences and matches by item alone. Once configured, encoding produces an overload pattern.

Advanced and overload encoding can be combined. Settings are saved with the current recipe and can be reused for repeated encoding. Reconfigure after changing inputs, outputs, or quantities, switching modes, clearing the page, or resetting the encoding type.

Middle-click a processing slot to edit its quantity and confirm. Setting it to 0 clears the slot.

<a name="pattern-upload"></a>

## Pattern Upload

With a pattern in the encoded-pattern slot, the **Upload** button sends it to its destination without manual carrying:

* **Crafting patterns** automatically match only provider groups for Molecular Assemblers, Extended Molecular Assemblers, EAE/EAEP Assembler Matrices, ECO Crafting Subsystems, and Tianshu Matter Warping Matrices. Matter Warping Matrices have priority; the other groups use the first compatible free slot in stable order.
* **Processing patterns** open the provider-selection screen: all visible Pattern Providers on the network are listed grouped by name, showing provider counts and free slots per group, with search by name, item, or tooltip.
* **Closed-loop patterns** upload to the bound Tianshu's Closed-Loop Pattern Storage.

The provider-selection screen also supports **alias mappings**. A JEI/EMI transfer uses the recipe type or viewer category as its source key; without viewer context, the pattern's primary output is used instead. The saved alias becomes the provider filter so similar recipes can locate their target faster. Filtering supports left-anchored item-ID matching, plus contains and wildcard matching for names and tooltips; pinyin matching is also available when Just Enough Characters is installed. Shift + right-click a list entry to bind that machine's name as an alias directly.

The terminal settings configure an **upload trigger**: holding a chosen key (or no key) while encoding automatically enters the matching upload flow; it can also be set to manual upload only. The manual upload button always remains available.

### Direct Encoding and Upload from JEI or EMI

Prepare a blank pattern in the Tianshu Pattern Encoding Terminal and use any non-closed-loop encoding mode. On a JEI or EMI recipe page, **hold `Alt` while transferring the recipe**. After the viewer confirms that the transfer succeeded, the terminal encodes the pattern and immediately attempts to upload it according to its type. A failed transfer starts neither encoding nor upload.

> This `Alt` shortcut belongs to the recipe viewer and is independent of the terminal's configured **upload trigger**. The upload trigger does not need to be set to `Alt`.

The direct-upload result depends on the pattern type:

* **Crafting, stonecutting, and smithing patterns** follow their normal automatic routing. When no selection is needed, the current JEI or EMI recipe page remains open and the upload result appears in the action bar, making consecutive transfers easier.
* **Processing, advanced, and overload patterns** use the same initial filter as the provider-selection screen: a saved alias for the current recipe type or category takes priority; otherwise the first default alias supplied by the viewer is used. If the filter produces exactly one candidate group with a free slot, the pattern uploads in the background and the current recipe page remains open. No candidate, multiple candidates, or a sole full candidate opens the provider-selection screen directly; returning from it goes back to the terminal.
* **Closed-loop mode** does not use this direct-upload shortcut. A JEI/EMI transfer only marks the primary output and starts closed-loop discovery; finish configuring, encoding, and uploading the closed-loop pattern normally.

<a name="closed-loop-patterns"></a>

## Closed-Loop Patterns

A closed-loop pattern bundles linked patterns into one recipe for automation that cycles ingredients while producing a net gain, such as Smithing Template duplication or the AdvancedAE Reaction Chamber Certus Quartz loop. Prepare the ordinary patterns that make up the loop first.

A closed-loop pattern consists of:

* **Member patterns** (up to 27): the ordinary patterns forming the loop, each annotated with copies executed per cycle. All copy counts must form a minimal integer ratio; 2:4 is invalid and must be written as 1:2.

* **Primary and secondary outputs:** Mark exactly 1 primary output and up to 8 secondary outputs from the loop's net production. The primary output must have a positive net gain. For example, consuming 128 Certus Quartz Crystals and producing 256 is valid, as is producing 192 Certus Quartz Crystals, 16 Certus Quartz Dust, and 16 Charged Certus Quartz Crystals. Producing only 64 Certus Quartz Crystals, 48 Certus Quartz Dust, and 48 Charged Certus Quartz Crystals has a negative net gain and cannot be encoded. Reversible conversions such as `1 Iron Block → 9 Iron Ingots` paired with `9 Iron Ingots → 1 Iron Block` also have no net gain and are rejected.

* **External inputs:** Materials each cycle draws from the network, computed automatically and shown read-only. Water supplied to the Reaction Chamber loop is one example.

* **Seeds:** Items advanced to start the loop, computed automatically and shown read-only. In the example loop, at least 80 Certus Quartz Crystals—or 16 Charged Certus Quartz Crystals plus 16 Certus Quartz Dust—must be available before crafting can start.

You must supply the materials required to start the loop.

### Authoring Workflow

1. Switch to Closed-Loop Pattern mode and place—or encode—the ordinary pattern for the loop's primary product in the terminal's **encoded-pattern output slot**.
For a Certus Quartz closed loop, this could be a processing pattern such as `16 Charged Certus Quartz Crystals + 16 Certus Quartz Dust → 64 Charged Certus Quartz Crystals`, or a crafting pattern such as `1 Certus Quartz Block → 4 Certus Quartz Crystals`; any ordinary pattern whose output is Certus Quartz can be used as the starting point.
2. Click **Fill**: the terminal searches the network for pattern combinations that close the loop around that product. When several candidates exist, clicking again cycles between them.
3. Open the **Details** screen to review and adjust: add, remove, and reorder members, edit per-cycle copies, and mark primary and secondary outputs (click to toggle declaration, Shift-click to set primary).
4. Adjust the two multipliers on the settings page:
   * **Job seed waves:** How many seed sets one job borrows at startup—effectively the job's parallelism. Higher values increase per-job throughput and the startup advance.
   * **Stored task sets:** How many jobs' worth of seeds the Tianshu keeps pre-stocked—effectively the number of jobs that can run in parallel.
5. When the status reads ready to encode, encode to obtain a <ItemLink id="ae2lt:closed_loop_pattern" />, then upload it into the Closed-Loop Pattern Storage. Authoring and encoding a closed-loop pattern needs no Tianshu; however, the pattern must be stored in some Tianshu's Closed-Loop Pattern Storage before it can execute.

### Preparing Seed Storage

To run a closed-loop pattern, install at least one Closed-Loop Pattern Storage and one Closed-Loop Seed Storage in the Tianshu Supercomputer, then place an **ME Storage Cell** compatible with the seed type inside it. The Seed Storage's 10 slots hold storage cells only; seed items cannot be inserted directly.

After installing the cell, select **Refill Seeds** on the closed-loop page. The terminal calculates the stored requirement for every enabled closed-loop pattern and transfers missing seeds from the current ME network into Seed Storage:

* **ME network lacks seeds:** The current network does not contain enough of the listed item or fluid. Add it to the network and try again.
* **Seed storage cannot accept seeds:** No suitable storage cell is installed, or its byte/type capacity is full, or a partition rejects the seed.
* **Seed refill incomplete:** Both kinds of problem are present. Hover over the status text to see the reason and amount for each seed.
* **Seeds ready:** The stored requirement is satisfied and the corresponding closed-loop crafting jobs can start.

Members can also be filled entirely by hand when no automatic candidate exists. An encoded closed-loop pattern can be re-inserted to load it for editing; encoding again updates the original pattern. A closed-loop pattern may itself be nested as a member of another loop; it is flattened during encoding, and the flattened member total must still not exceed 27.

The status line reports the specific reason a draft cannot encode — for example an unreadable member pattern, non-minimal copy ratios, a missing primary output, or a loop whose inputs and outputs do not balance.

<a name="inventory-maintenance"></a>

## Inventory Maintenance

When a formed Tianshu Supercomputer is available on the network, the terminal can configure automatic restock rules per item: crafting starts when the stored amount drops **below** the lower bound, stops when it **reaches** the upper bound, and each job requests the configured batch size.

### Configuration

* **Shift + middle-click** a craftable item in the terminal to open its Inventory Maintenance editor, which works much like an ME Requester.
* The **Inventory Maintenance** button opens an overview listing every configured entry, including materials whose current stock is zero. Click an entry to edit its rule.
* The **Maintainable** view filters the terminal list to materials configured for inventory maintenance, making them easier to review together.

* **Start below:** Starts automatic crafting when the material falls below the configured value.
* **Stop at:** Stops starting new automatic crafting jobs when the material reaches the configured value. Jobs already submitted continue to completion.
* **Per job:** Each job requests the full configured amount, even when less is needed to reach the upper bound. The resulting stock may exceed that bound.
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
**Exact match** protects only the selected NBT variant, including properties such as durability and enchantments. **Ignore NBT** groups all variants with the same item ID.
**Reserve** sets the protected quantity. Set it to `-1` to reserve all existing stock.

If the overview becomes a recovery page, zero out or delete old entries one by one to reveal the remaining entries.

<a name="settings"></a>

## Terminal Settings

**Terminal Settings → Tianshu Settings** opens this page. Wired and wireless terminals share these settings.

### Encoding Terminal Settings

* **Upload trigger:** By default, encoding uploads unless Shift is held. Other options upload while holding Shift, Ctrl, or Alt, or require manual upload.
* **Intercept duplicate encoding:** Enabled by default. Encoding with upload is blocked if the network already contains the same pattern. With the default trigger, hold Shift to skip upload and duplicate interception.

### JEI Wireless Ingredients

Enable **JEI wireless ingredients** (off by default), then carry a powered, connected Wireless Tianshu Pattern Encoding Terminal in your inventory or Curios. When transferring a recipe to a workbench or machine with JEI transfer support, missing items are supplied from the network. Tianshu modules in a Wireless Universal Terminal work too.

Keep inventory space free for ingredients. A normal click fills one recipe; Shift fills in bulk. Only items are supplied. If transfer fails or you close the screen, extracted items remain in your inventory.
