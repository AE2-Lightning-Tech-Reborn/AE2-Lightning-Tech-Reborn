---
navigation:
  title: Large Overload Processing Factory
  icon: ae2lt:large_overload_controller
  parent: machines/machines-index.md
  position: 80
item_ids:
  - ae2lt:large_overload_controller
  - ae2lt:large_overload_frame
  - ae2lt:large_overload_casing
  - ae2lt:large_overload_core_t1
  - ae2lt:large_overload_core_t2
  - ae2lt:large_overload_core_t3
  - ae2lt:large_overload_core_t4
  - ae2lt:large_overload_pattern_hatch
  - ae2lt:large_overload_expanded_pattern_hatch
  - ae2lt:large_overload_crystal_hatch
  - ae2lt:large_overload_process_core_hatch
  - ae2lt:large_overload_energy_hatch
  - ae2lt:large_factory_recovery_capsule
---

# Large Overload Processing Factory

This fixed **9 × 7 × 9** factory executes processing recipes synchronously. The central processing core sets one operation budget shared by every hatch. A processing pattern that represents four source recipes uses four operations and pays four recipe costs.

## Construction

Place the Controller in the center of the front face. The factory extends four blocks to each side, three above and below, and eight blocks behind it. Place the processing core four blocks directly behind the Controller. Keep the remaining interior empty.

<GameScene zoom="2.5" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/large_overload_factory.snbt" />
  <DiamondAnnotation pos="4.5 3.5 0.5" color="#85f29e">Controller</DiamondAnnotation>
  <DiamondAnnotation pos="4.5 3.5 8.5" color="#f2d37a">Rear 3 × 3 hatch area</DiamondAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

| Part | Required |
| --- | ---: |
| Frame, along the twelve edges | 84 |
| Fixed casing | 228 |
| Controller | 1 |
| Central processing core | 1 |
| Rear hatch positions, filled with hatches or casing | 9 |

At least one Pattern Hatch or Crystal Hatch is required. A factory may have several processing hatches, but only **one Process Core Hatch**. Each hatch occupies one of the nine rear positions. A layout with one process hatch, one energy hatch and seven expanded pattern hatches holds **1,008 patterns**.

The Controller's **Preview** button marks structure positions with particles. Blue marks frame/casing, purple the central core, yellow rear hatch positions and red obstructed interior air. **Build casing** uses blocks from your inventory in small batches, places only into empty positions and respects placement checks. Place the core and your chosen hatches yourself. The Controller reports missing materials and the coordinates of incorrect blocks.

## Hatches and networks

* **Pattern Hatch:** 36 encoded processing patterns.
* **Expanded Pattern Hatch:** 144 patterns over four pages. All pages remain available to crafting CPUs.
* **Crystal Hatch:** nine resident catalyst slots by default. Inserting a catalyst exposes its crystal or dust recipes as virtual processing patterns. Their real fluid inputs remain required; the resident catalyst is not consumed. Disable unwanted recipe entries with their buttons.
* **Process Core Hatch:** nine slots by default. Process cores unlock recipe families; duplicates do not increase throughput.
* **Energy Hatch:** optional FE input. Multiple energy hatches share the central core's FE buffer and throughput.

Each processing hatch uses **one ME channel** and connects only on its rear outward face. It can use its own ME network. Its materials, Lightning, automatic ME energy and ordinary returns use that same network. Process Core and Energy Hatches have no ME nodes or channels. Nine processing hatches on one network require sufficient channel capacity.

Each processing hatch has two mutually exclusive modes. **CPU mode** publishes its enabled patterns. **Passive mode** withdraws them from CPUs and crafts when its own network has materials. Passive mode extracts a real first sample before recipe matching, counts that sample once, and expands the batch within the shared budget. Idle checks occur about every five seconds; successful work can wake further work in the same tick.

## Recipe families

Ordinary T1–T4 factories support the final Overload Processing and AdvancedAE Reaction Chamber recipe catalogues without unlock items. This includes compatible recipes already added to those catalogues.

| Process core | Additional source recipes |
| --- | --- |
| Simulation | LT Lightning Simulation Chamber |
| Assembly | LT Lightning Assembly Chamber |
| Catalyzer | LT Crystal Catalyzer, through resident catalysts in a Crystal Hatch |
| Integrated Workstation | NeoECO AE integrated working station |
| Crystal Aggregator | AE2 Crystal Science crystal aggregator |
| Crystal Pulverizer | AE2 Crystal Science crystal pulverizer |
| Circuit Etcher | AE2 Crystal Science circuit etcher |
| Crystal Assembler | ExtendedAE crystal assembler |

Insert ordinary processing patterns as usual. The factory validates the actual input signature and **every output** on the first execution. Merely inserting a pattern does not prove that its recipe is supported. Recipe/tag reloads invalidate bindings; removing a process core stops its recipes. Already produced items remain recoverable.

## Throughput and costs

Default ordinary core budgets are **16,384 / 65,536 / 262,144 / 1,048,576 source operations per tick** for T1–T4. Actual batches also require FE, Lightning, input availability and server work budget. Ordinary FE cost is **twice the source recipe's total FE**. These values, FE throughput/capacity and the default auxiliary inventory sizes are configurable under `largeOverloadFactory` in the common config.

Every ordinary source operation costs at least **one High Voltage Lightning**. A higher native Lightning cost is preserved. A recipe requiring EHV can substitute **four HV for one EHV**, including mixed EHV/HV payments. This is built in: no substitution matrix is required. EHV cannot pay an HV requirement.

By default the factory spends external buffered FE first, then withdraws only the shortage from the working hatch's ME energy service. **External FE only** disables new automatic ME energy withdrawals. The ME network itself must still be powered.

## Firmament formation

A natural **Firmament Conversion Core inside its starship** can replace the central processing core. Build around it in place. Empty its original inventory and finish its old job before formation. This mode requires a Pattern Hatch and supports **only Firmament Conversion recipes**.

Its limit is **1,024 source operations per tick**, shared by all hatches. Each operation costs **one EHV**, or four HV through substitution, and no processing FE. A full 1,024-operation tick therefore costs 1,024 EHV or 4,096 HV. The core's original manual processing and automation are disabled while the factory owns it; formation does not reroll its natural loot.

## Returns and recovery

Time Wheel and Tianshu CPUs can receive queued outputs after committing their dispatch, enabling same-tick chains. Other CPUs receive products through the hatch's ME network on a later tick. Synchronous processing does not mean unlimited server work: exhausted budgets continue on later ticks.

If storage is full, a network changes or a structure breaks, already owned materials and products remain in a saved resource account. Automatic returns require the original external network anchor. **Export recovery** creates a capsule for retained resources; breaking a hatch or an energized Controller also preserves owned resources in a capsule. Use the capsule on an active processing hatch to explicitly return those resources to that network. Partial returns remain in the same capsule. Copying a capsule does not copy its resources.
