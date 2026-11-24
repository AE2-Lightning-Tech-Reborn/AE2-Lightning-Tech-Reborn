---
navigation:
  title: Processing Machines
  icon: ae2lt:lightning_simulation_room
  parent: index.md
  position: 30
---

# Processing Machines

AE2 Lightning Tech processing machines combine ME-stored Lightning with FE or AE power. Use the table below to compare slots, internal buffers, matrix capacity, and upgrade support across all machines:

<SubPages />

## Machine Overview

| Machine | Slots & Internal Buffers | Matrix Slots | Upgrade Cards | Power & Lightning |
|---------|--------------------------|-------------:|---------------|-------------------|
| [Lightning Simulation Room](lightning-simulation-chamber.md) | 3 input, 1 output (8,192/slot); 20,000,000 FE | 1 | Up to 4 Acceleration Cards | FE + recipe Lightning |
| [Lightning Assembly Chamber](lightning-assembly-chamber.md) | 9 input, 1 output (8,192/slot); 20,000,000 FE | 1 | Up to 4 Acceleration Cards | FE + recipe Lightning |
| [Overload Processing Factory](overload-processing-factory.md) | 9 item input, 1 item output (16,384/slot); 1,024,000 mB input & output tanks; 640,000,000 FE | 32 | Up to 4 Acceleration Cards | FE + recipe Lightning |
| [Crystal Catalyzer](crystal-catalyzer.md) | 256 catalyst, 16,384 output, 16,000 mB fluid tank; 1,000,000 FE | 1 | 1 Redstone Card | FE + fluid + recipe Lightning |
| [Pigmee Crystal Catalyzer](crystal-catalyzer.md) | 64 catalyst (full stack required), 64 output; water only | None | None | 1,000 mB Water only, fixed 100 ticks |
| [Mining Factory](mining-factory.md) | 4,096 block input, 1 tool, 9×4,096 output; 4,000,000 FE | 8 | None | FE + 1 HV Lightning per batch |
| [Atmospheric Ionizer](atmospheric-ionizer.md) | 1 condensate slot | None | None | Direct ME AE power (no FE or Lightning) |

## Power & Automation Notes

* **Power Types**: The Simulation Room, Assembly Chamber, Overload Processing Factory, standard Crystal Catalyzer, and Mining Factory consume **FE** to progress and deduct **Lightning** from ME storage on completion. The **Atmospheric Ionizer** draws **AE** directly from the ME network. The **Pigmee Crystal Catalyzer** requires neither energy nor Lightning.
* **Side Configuration & Auto-Export**: Configure export faces and auto-eject in each machine's UI to return outputs directly to adjacent inventories or [Overloaded Pattern Providers](../overloaded-network/overloaded-pattern-provider.md).

<OptionalSection modId="appflux">
* **Applied Flux Network Refill**: With Applied Flux installed, FE-powered machines (Simulation Room, Assembly Chamber, Processing Factory, standard Crystal Catalyzer, and Mining Factory) automatically refill their internal FE buffers from FE stored in the connected ME network.
</OptionalSection>
