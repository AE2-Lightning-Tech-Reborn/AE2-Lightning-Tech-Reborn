---
navigation:
  title: Overloaded Pattern Provider
  icon: ae2lt:overloaded_pattern_provider
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_pattern_provider
  - ae2lt:overloaded_pattern_provider_upgrade
  - ae2lt:overload_pattern
  - ae2lt:overload_pattern_encoder
  - ae2lt:overloaded_wireless_connect_tool
---

# Overloaded Pattern Provider

<ItemGrid>
  <ItemIcon id="ae2lt:overloaded_pattern_provider" />
  <ItemIcon id="ae2lt:overloaded_pattern_provider_upgrade" />
  <ItemIcon id="ae2lt:overload_pattern_encoder" />
  <ItemIcon id="ae2lt:overload_pattern" />
  <ItemIcon id="ae2lt:overloaded_wireless_connect_tool" />
</ItemGrid>

The **Overloaded Pattern Provider** holds **36 patterns** and supports adjacent or same-dimension wireless face dispatch, active output return, adaptive batching, and per-slot NBT-ignoring Overload Patterns. Use an **Overloaded Pattern Provider Upgrade** on an existing compatible provider to upgrade it in place while preserving supported inventory and settings.

<RecipeFor id="ae2lt:overloaded_pattern_provider_upgrade" />

## Overloaded Wireless Connect Tool

One tool manages wireless face connections for Overloaded Pattern Providers, Overloaded ME Interfaces, and Overloaded Power Supplies:

1. Right-click the source provider to select it.
2. Right-click the target face on a machine to add a connection (right-click an already linked face to remove it).
3. **Ctrl + Right-Click** toggles connections across adjacent connected identical blocks starting from the clicked target.
4. Limits: **Same dimension only**, default max distance **128 blocks** (configurable; `0` disables distance checks), and up to **1,024** bound target faces per provider.

## Dispatch Modes & Strategies

* **Normal vs. Wireless**: Normal mode pushes to adjacent targets; Wireless mode dispatches to bound target faces. One execution of a pattern goes to a single target; for machines requiring distinct faces per ingredient, use directional patterns or a compatible layout.
* **Blocking & Distribution**: Standard blocking checks whether the target already contains relevant inputs; same-pattern blocking compares against the last accepted pattern. Distribution strategies control multi-target selection order.
* **Adaptive Scaling**: Probes compatible targets and patterns to push larger batches based on active demand and available target space.

## Output Return & Input Filtering

* **Return Modes**: Choose disabled, active auto-return, or passive acceptance of machine auto-exports.
* **Active Return Timing**: Adjacent and wireless targets share one backoff poller—starting at **20 ticks** when active and backing off to **40 → 80 → 128 ticks** while idle. Any new dispatch or returned output resets the interval to **20 ticks**, and AUTO mode also sweeps completed outputs before dispatching new ingredients.
* **Input Filtering**: When enabled, active extraction and passive acceptance only admit items matching outputs declared by loaded patterns (respecting Overload Pattern match rules). Disabling it allows undeclared byproducts and other extractable contents from the target face.

<OptionalSection modId="mekanism">
* **Mekanism Chemical Support**: With Mekanism installed, providers support item, fluid, and chemical I/O through compatible machine sides and adapters.
</OptionalSection>

<OptionalSection modId="appflux">
* **Applied Flux Induction Card**: Installing an Induction Card lets the provider supply FE to its target machines (independent of Frequency Card FE settings on the machine itself).
</OptionalSection>

## Overload Patterns & Encoder

Use **Overload Patterns** when recipes involve varying durability, enchantments, or NBT components:

1. Insert any valid **processing pattern** (or existing Overload Pattern) as the source pattern in the **Overload Pattern Encoder**; ingredients and counts are inherited from the source.
2. Click individual input or output slots in the preview to toggle each slot between **Ignore NBT: Off** (strict full-component match) and **Ignore NBT: On** (item ID only).
3. Encode to produce the **Overload Pattern**. You can also encode Overload Patterns directly in the [Tianshu Pattern Encoding Terminal](../terminals/pattern-encoding-terminal.md).
