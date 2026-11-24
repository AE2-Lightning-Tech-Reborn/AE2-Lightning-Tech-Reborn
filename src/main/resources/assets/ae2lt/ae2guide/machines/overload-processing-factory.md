---
navigation:
  title: Overload Processing Factory
  icon: ae2lt:overload_processing_factory
  parent: machines/machines-index.md
item_ids:
  - ae2lt:overload_processing_factory
---

# Overload Processing Factory

<ItemImage id="ae2lt:overload_processing_factory" scale="2" float="left" />

The **Overload Processing Factory** is the primary bulk processing machine in AE2 Lightning Tech, supporting combined item and fluid I/O alongside multi-matrix parallel scaling.

## Slots & Buffers

* **Item Slots**: 9 item inputs and 1 item output, each holding up to **16,384** items.
* **Fluid Tanks**: **1,024,000 mB** input tank and **1,024,000 mB** output tank. A recipe may define up to one item output and one fluid output simultaneously.
* **Matrix Slot**: Holds up to **32** <ItemLink id="ae2lt:lightning_collapse_matrix" /> items. Sneak-right-click the factory with matrices to quick-insert up to the cap.
* **Energy Buffer**: **640,000,000 FE** by default.

## Acceleration Cards & Processing Rate

The factory accepts up to 4 AE2 <ItemLink id="ae2:speed_card" />s, scaling its default per-tick FE throughput:

| Acceleration Cards | Default Max FE/t |
|-------------------:|-----------------:|
| 0 | 400,000 FE/t |
| 1 | 2,000,000 FE/t |
| 2 | 8,000,000 FE/t |
| 3 | 32,000,000 FE/t |
| 4 | 128,000,000 FE/t |

## Parallelism & Energy Formula

* **Parallel Limit**: With no matrix installed, the factory processes **1 batch** per cycle. Each installed matrix grants **8 parallel operations** by default, up to **256** at 32 matrices.
* **Effective Batch Size**: The locked batch size is bounded by all five constraints simultaneously: matrix parallel cap, available item and fluid inputs, free item output space, free output fluid tank space, and available ME Lightning stock. Keeping outputs drained is essential for sustaining full parallelism.
* **Lightning & Substitution**: Lightning cost scales linearly with batch count. With at least 1 Lightning Collapse Matrix installed, any Extreme High Voltage deficit after using available EHV is covered by High Voltage Lightning at a **4:1** ratio.
* **Parallel FE Cost**: Let single-batch FE be `E`, locked parallel count be `p`, and max parallel cap be `M`:
  * When `M ≤ 1`, FE scales linearly (`E × p`).
  * When `M > 1`, total FE is `ceil(E × p × (p + 2M − 3) / (2M − 2))`.

<OptionalSection modId="appflux">
## Network FE Refill

With Applied Flux installed, the factory automatically draws FE from the connected ME network to refill its 640,000,000 FE internal buffer.
</OptionalSection>

<OptionalSection modId="advanced_ae">
## AdvancedAE Reaction Chamber Compatibility

With AdvancedAE installed, the Overload Processing Factory can run compatible Reaction Chamber recipes (single item or single fluid output) using their original ingredients, fluids, outputs, and base FE while applying the factory's own parallelism, speed cards, and buffer limits. Compatible recipes appear directly in the factory's recipe viewer tab.
</OptionalSection>
