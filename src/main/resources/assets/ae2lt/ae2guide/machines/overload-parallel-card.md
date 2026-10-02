---
navigation:
  title: Overload Parallel Card
  icon: ae2lt:overload_parallel_card
  parent: machines/machines-index.md
item_ids:
  - ae2lt:overload_parallel_card
---

# Overload Parallel Card

The **Overload Parallel Card** is an optional LT and AE2 Crystal Science integration. Craft one in the Lightning Assembly Chamber from two AE2CS Meteorite Overclock Cards, LT components, and Extreme High Voltage Lightning. Up to two can be installed per supported AE2CS machine.

One card allows up to **8 operations per game tick**; two cards allow up to **64**. Every operation still pays its full AE power and input cost and checks fluids and output capacity. The machine's internal AE power capacity grows to 8 or 64 times its original size, and CS's normal grid connection charges it. Processing stops when power, inputs, fluids, or output space run out. Recipes that cost more than the native buffer may still need multiple passes. At 20 TPS, with sufficient resources and output space, the caps are **160 or 1,280 recipes per second**.

AE2CS **1.2.1** on MC 1.21.1 supports the Circuit Etcher, Crystal Pulverizer, Crystal Aggregator, and Entropy Variation Reaction Chamber. The current **1.3.0 snapshot** also supports the Crystal Infuser and Pulse Centrifuge. The Crystal Growth Chamber performs up to 8 or 64 paid growth passes per tick, stopping when seeds mature. The Crystal Vibration Chamber and Meteorite Pattern Provider retain their native upgrade behavior.

AE2CS already includes Meteorite Overclock Cards: two cards make most recipes take one tick, but each processor still completes at most one recipe per tick. The LT card increases throughput beyond that limit. Additional Speed or Meteorite Overclock Cards do not increase its 8 or 64 operation limit.
