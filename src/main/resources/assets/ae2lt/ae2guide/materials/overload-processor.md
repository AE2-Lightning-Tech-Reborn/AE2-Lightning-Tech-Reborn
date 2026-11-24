---
navigation:
  title: Overload Processor
  icon: ae2lt:overload_processor
  parent: materials/materials-index.md
item_ids:
  - ae2lt:overload_processor
  - ae2lt:overload_circuit_board
  - ae2lt:unoverloaded_circuit_board
  - ae2lt:overload_inscriber_press
---

# Overload Processor

<ItemGrid>
  <ItemIcon id="ae2lt:overload_processor" />
  <ItemIcon id="ae2lt:overload_circuit_board" />
  <ItemIcon id="ae2lt:unoverloaded_circuit_board" />
  <ItemIcon id="ae2lt:overload_inscriber_press" />
</ItemGrid>

**Overload Processors** are fundamental circuit components required by most Overloaded machines and network blocks.

## Standard Crafting Path

1. **Obtain the Overload Inscriber Press**: Convert the three AE2 processor presses via ground lightning conversion (check your recipe viewer for exact ingredients).
2. **Press Unoverloaded Circuit Boards**: Press **Overload Crystals** with the **Overload Inscriber Press** inside an AE2 <ItemLink id="ae2:inscriber" />.
3. **Convert to Overload Circuit Boards**: Use ground lightning conversion or process in a [Lightning Simulation Room](../machines/lightning-simulation-chamber.md) with Overload Crystal Dust, FE, and High Voltage Lightning.
4. **Assemble Overload Processors**: Combine the **Overload Circuit Board**, Redstone Dust, and Printed Silicon in an AE2 Inscriber.

<RecipeFor id="ae2lt:unoverloaded_circuit_board" />

<RecipeFor id="ae2lt:overload_processor" />

## Bulk Production Routes

* **Overload Processing Factory**: Bypass Inscriber steps in the [Overload Processing Factory](../machines/overload-processing-factory.md) using Overload Crystal Blocks, Blocks of Redstone, and Blocks of Silicon with FE and High Voltage Lightning (check your recipe viewer for exact counts and energy costs).

<OptionalSection modId="extendedae">
* **ExtendedAE Integration**: With ExtendedAE installed, slice Overload Crystal Blocks into **Unoverloaded Circuit Boards** in a **Circuit Slicer**, and when enabled in config, batch-craft **Overload Processors** in a **Crystal Assembler**.
</OptionalSection>

## Item Reference

| Item | Role |
|------|------|
| Overload Inscriber Press | Used in an Inscriber to press Unoverloaded Circuit Boards |
| Unoverloaded Circuit Board | Intermediate board requiring lightning conversion or a Simulation Room to overload |
| Overload Circuit Board | Combined with Redstone Dust and Printed Silicon in an Inscriber |
| Overload Processor | Finished processor used across machines, network devices, and high-tier components |
