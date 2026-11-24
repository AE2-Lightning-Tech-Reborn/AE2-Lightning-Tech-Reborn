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

<ItemGrid>
  <ItemIcon id="ae2lt:tianshu_pattern_encoding_terminal" />
  <ItemIcon id="ae2lt:wireless_tianshu_pattern_encoding_terminal" />
  <ItemIcon id="ae2lt:closed_loop_pattern" />
</ItemGrid>

Available in wired and wireless forms, the **Tianshu Pattern Encoding Terminal** handles multi-mode encoding, smart upload routing with aliases, closed-loop pattern authoring, and inventory maintenance management.

<RecipeFor id="ae2lt:tianshu_pattern_encoding_terminal" />

<RecipeFor id="ae2lt:wireless_tianshu_pattern_encoding_terminal" />

## Encoding Modes & Processing Tools

Five permanent modes are available: **Crafting**, **Processing**, **Smithing**, **Stonecutting**, and **Closed-loop Pattern**.

* **Processing Controls**:
  * Scale recipe quantities with `×2 / ÷2 / ×5 / ÷5` (hold `Shift` for `×4 / ÷4 / ×10 / ÷10`).
  * **Middle-click** any slot to edit its count directly (`0` clears the slot).
* **Overload Pattern Setup**: Open **Overload Pattern Setup** on a processing recipe to toggle individual inputs or outputs between **Strict** (full component match) and **ID Only** (ignore NBT). Re-verify setup if you change ingredients, counts, or mode.

<OptionalSection modId="advanced_ae">
* **Advanced Encoding (AdvancedAE)**: With AdvancedAE installed, assign per-input insertion faces on processing patterns—combinable with Overload Pattern Setup.
</OptionalSection>

<OptionalSection modId="useless_mod">
* **Omniversal Patterns (Useless Mod)**: With Useless Mod installed, an **Omniversal patterns** mode is added. Fill from your recipe viewer or set inputs, outputs, and molds manually. Upload prioritizes formed multiblock ME Pattern Assemblies with matching molds and free slots before matching single-block alloy furnaces (multi-mold recipes route exclusively to multiblocks). If no valid match exists, the encoded pattern stays in the terminal.
</OptionalSection>

## Upload Routing, Aliases & Undo

### Target Routing

| Pattern Type | Upload Target |
|--------------|---------------|
| Crafting, Stonecutting, Smithing | Prioritizes the [Tianshu Matter Warping Matrix](../matrix/matrix-index.md), then compatible allowlisted groups (AE2 Molecular Assemblers, ExtendedAE / ExtendedAE Plus assembly lines, NeoECOAE groups, AE2CS Meteorite Pattern Providers, etc.) |
| Processing, Advanced, Overload | Choose from visible provider groups, or auto-upload when a single unique group matches |
| Closed-Loop Patterns | Uploaded to the Closed-Loop Pattern Storage of the [Tianshu Supercomputer](../tianshu/tianshu-index.md) locked by this terminal session |
| Omniversal Patterns (if enabled) | Routed by recipe, molds, and free slots to matching alloy furnaces or multiblocks |

### Shortcuts & Safeguards

* **Upload Trigger**: By default, clicking Encode encodes and uploads, while `Shift`-clicking encodes only (configurable to modifier-upload or manual-only). Duplicate upload blocking is enabled by default.
* **Alt Recipe Transfer**: Holding `Alt` during JEI/EMI recipe transfer waits for transfer completion and triggers encode/upload automatically (staying in the viewer on direct upload, or opening group selection when needed; in Closed-Loop mode it searches loop solutions for the transferred target).
* **Manage Upload Aliases**: `Shift + Right-Click` the encode button to open **Manage upload aliases**. Save workstation-to-group aliases and choose **Off**, **Saved aliases**, or **Any match**:
  * Processing auto-upload only proceeds when merged candidates resolve to **one unique writable group**; multiple matches, no match, or full groups fall back to manual selection.
  * Aliases are stored client-side and support **Export clipboard** / **Import clipboard** with conflict confirmation.
* **Undo Last Upload**: With the encoded output slot empty, **Right-Click** the encode button to recall your own last successful upload from the current session (rejected if the pattern was moved, replaced, or modified; cleared when closing the terminal).

## Closed-Loop Patterns & Seed Refilling

A **Closed-Loop Pattern** groups interconnected patterns into a net-positive loop declaring **1 net primary output** and up to **8 byproducts** across up to **27 member slots** (in lowest integer ratios; nested closed-loop patterns count toward the expanded member limit; net-zero compression/decompression loops are rejected):

1. **Discover or Assemble**: Place a pattern for the desired primary output in the output slot (or transfer it from a recipe viewer), switch to **Closed-loop Pattern**, and trigger auto-discovery to cycle through valid network loop solutions, or add members manually.
2. **Configure & Encode**: Adjust member order, per-cycle counts, primary vs. byproduct roles, job borrow multiplier, and seed storage multiplier; resolve any validation notices, then encode.
3. **Upload & Refill Seeds**: Upload to a **Closed-Loop Pattern Storage** (36 slots each) on an online Tianshu Supercomputer, install compatible ME storage cells in a **Closed-Loop Seed Storage** (10 cell slots each), and click **Refill Seeds** to move required seed stock from the ME network (status messages distinguish missing network stock, rejected bay insertion, and ready state).
4. **Session Locking**: Opening the terminal locks onto the first available Tianshu Supercomputer without rebinding mid-session; reopen the terminal if the target goes offline or changes (authoring drafts works without an online supercomputer).

## Inventory Maintenance & Reserved Inventory

With an online Tianshu Supercomputer (up to **2,048** rules), `Shift + Middle Click` a craftable item or open the **Inventory Maintenance** overview (use the maintainable filter view to inspect all rules, including zero-stock entries):

* **Hysteresis & Fixed Batch Size**: Restocking activates when stock is **strictly below** **Start below** and stops upon reaching **Stop at**. No duplicate job is placed while a rule's previous job is active. Every order submits the fixed batch count, which may overshoot **Stop at**, and running jobs are never canceled upon reaching the upper threshold.
  * *Example*: With **Start below** `100`, **Stop at** `200`, and batch `128`, dropping to `90` orders `128`; with no other consumption, stock finishes at `218` and restocking stops.
* **Reserved Inventory**: Set global reserves and per-rule reserves; both layers apply simultaneously (the stricter bound wins), supporting exact-component and component-ignoring matching. Setting `-1` protects all existing stock of that entry. Reserves apply **only to maintenance planning** and do not block manual crafting or other ME automation.
