---
navigation:
  title: Overloaded Cable
  icon: ae2lt:overloaded_cable
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_cable
  - ae2lt:overloaded_cable_white
  - ae2lt:overloaded_cable_orange
  - ae2lt:overloaded_cable_magenta
  - ae2lt:overloaded_cable_light_blue
  - ae2lt:overloaded_cable_yellow
  - ae2lt:overloaded_cable_lime
  - ae2lt:overloaded_cable_pink
  - ae2lt:overloaded_cable_gray
  - ae2lt:overloaded_cable_light_gray
  - ae2lt:overloaded_cable_cyan
  - ae2lt:overloaded_cable_purple
  - ae2lt:overloaded_cable_blue
  - ae2lt:overloaded_cable_brown
  - ae2lt:overloaded_cable_green
  - ae2lt:overloaded_cable_red
  - ae2lt:overloaded_cable_black
---

# Overloaded Cable

<ItemGrid>
  <ItemIcon id="ae2lt:overloaded_cable" />
  <ItemIcon id="ae2lt:overloaded_cable_white" />
  <ItemIcon id="ae2lt:overloaded_cable_light_blue" />
  <ItemIcon id="ae2lt:overloaded_cable_orange" />
  <ItemIcon id="ae2lt:overloaded_cable_purple" />
  <ItemIcon id="ae2lt:overloaded_cable_black" />
</ItemGrid>

**Overloaded Cables** are high-capacity dense ME cables available in Fluix plus all 16 dye colors (17 variants total). They follow standard AE2 color-separation rules and can be recolored with the [Rainbow Pigmee Fumo](../materials/rainbow-pigmee.md).

## Mixed Topology & Path Bottlenecks

* **High-Capacity Backbone**: Connections running purely across Overloaded ME Controllers and Overloaded Cables carry high-capacity channel traffic well beyond standard dense cables.
* **Standard Cable & Device Limits**: Whenever a route passes through standard AE2 cables (Glass, Smart, or Dense) or standard passthrough blocks, that segment remains bound by its own channel limit and AE2's channel mode.
* **Troubleshooting**: If downstream devices report missing channels, trace the entire path from controller to device to ensure no standard cable or low-capacity passthrough block is bottlenecking the branch.
