---
navigation:
  title: Overloaded Frequency Card
  icon: ae2lt:overloaded_frequency_card
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_frequency_card
---

# Overloaded Frequency Card

<ItemGrid>
  <ItemIcon id="ae2lt:overloaded_frequency_card" />
  <ItemIcon id="ae2lt:advanced_wireless_overloaded_controller" />
</ItemGrid>

The **Overloaded Frequency Card** connects ME devices or parts directly to a wireless frequency without placing a Wireless Receiver. It also supports automatic connections when placing devices and wireless FE power.

The card only links targets. The frequency itself must still be transmitted by an **Advanced Wireless Overloaded Controller**. Frequencies broadcast by normal Wireless Overloaded Controllers cannot be used for frequency-card links.

## Binding

1. Put an **Advanced Wireless Overloaded Controller** on the target frequency
2. Hold an Overloaded Frequency Card and **Shift + right-click** that controller
3. After binding, the card shows the frequency name and records the bound player

A bound card can only be used by the player who bound it. **Shift + right-click air** clears the binding, while keeping the auto-connect setting.

Hold the card in either hand and **right-click air** to open its frequency screen. The top navigation provides Overview, Frequency, Members, Network management, and Frequency settings; see [Wireless Network](wireless-network.md#frequency-screen) for details. The Frequency page can search and bind existing frequencies or create a new one. Cards cannot broadcast on their own; newly created frequencies still require an Advanced Wireless Overloaded Controller to carry ME channels.

Once bound to an available advanced transmitter, the card provides network management for inspecting devices, power, and frequency connections, locating targets, and opening supported native device screens. Member and frequency settings remain subject to permissions.

Settings in the screen apply to the card used to open it; reopen the screen if the held item is changed.

## Manual Linking

Hold a bound frequency card and right-click a supported ME network device, selectable part, or cable body to toggle the entire physical ME cluster.

A physical ME cluster is a group of devices connected directly through cables or adjacent grid nodes. New devices added to the group share its frequency, and wireless access expands with channel demand. Groups on the same frequency merge into one managed group when physically connected. If removing cables separates them, each surviving group retains the frequency. Right-click any device, part, or cable in the group to disconnect the entire group.

Controllers and blocks without an available grid node cannot be selected. A physical cluster can only use one frequency at a time. If clusters carrying different frequencies are physically joined, wireless connections are suspended while the existing frequency bindings are retained. Splitting the physical cluster restores each component's own link. Alternatively, right-click with either matching card to remove only that card's frequency and let the remaining frequency recover.

If the card reports that the frequency is temporarily unavailable, the transmitter is usually not loaded. The link will recover once the Advanced Wireless Overloaded Controller is loaded again.

Configure **Toggle Frequency Card Link** in the game controls (unassigned by default) to toggle an in-range target without right-clicking it. The shortcut uses an eligible card from either hand, Curios, the hotbar, or the main inventory. If several cards share the highest priority, hold the intended card to disambiguate.

## Wireless FE Power

With Applied Flux installed, an Advanced Wireless Overloaded Controller can also send FE stored in its ME network to devices that accept FE. Right-click a device with the bound card to toggle power. The clicked face is checked first, followed by the other five faces, stopping at the first input. For a device supporting both ME and FE, an ordinary right-click handles both connections. Interfaces and Pattern Providers, including their panel versions, cannot receive wireless FE through this feature; their ME links remain available.

Hold **Ctrl + right-click** to toggle a group of devices. The group follows face-adjacent blocks of the **same block type**, including different orientations. If the clicked device is powered by this frequency, the group is disconnected; otherwise, the group is connected. Each device selects its own FE input face. Devices on another frequency or without permission are skipped, and the result reports counts. This action changes FE only and preserves ME links. It also works while holding a wireless terminal with the card installed.

Bulk operations are queued and processed in batches on the server. Completion reports changed, unchanged, and skipped counts with skip reasons. Wait for an existing bulk task to finish before starting another. Only loaded chunks are traversed. If more than 8192 contiguous matching blocks are discovered, the operation is cancelled during the discovery phase without altering any connections. If an active batch task is aborted midway, already applied changes are retained.

## Auto Connect

The card overview has two independent switches:

* **ME** (frequency auto connect) connects compatible blocks or AE2 parts to wireless ME channels when placed.
* **FE** (FE auto connect) verifies a placed machine's FE input before connecting wireless power.

Turning a switch off only stops future automatic connections. Existing links remain connected. Select and unbind existing wireless power targets from **Network management → FE power** on the controller or card.

Open the held card or its configuration inside a terminal to change either setting. Supported wireless terminals also provide two separate toolbar switches. **Shift-scroll** and the **Toggle Frequency Card Auto Connect** keybind change frequency auto connect only, leaving FE unchanged.

Bulk placement with **Construction Sticks** also applies the selected card’s ME and FE settings independently. When a machine is placed, a small energy probe is sent to observe its internal storage after running. If no FE remains stored or storage cannot be reliably verified, automatic binding is skipped to ensure safety; manual linking remains available.

Each automatic connection type considers only cards with its corresponding switch enabled. If several cards qualify, hold the intended card or turn off the corresponding setting on the others.

## Curios Slot

When Curios is installed, the Overloaded Frequency Card can be placed in its dedicated Curios slot. A card in this slot participates in auto connect and keybind toggling; carrying other cards can still cause the multiple-candidate warning.

Manual right-click linking still requires holding the card, or holding a wireless terminal with a bound card installed.

## Wireless Terminals

When AE2WTLib is installed, the Overloaded Frequency Card can be installed in a wireless terminal's upgrade slot. The terminal screen adds frequency-card buttons for selecting a frequency, clearing the binding, or toggling auto connect.

Holding a wireless terminal with a bound card installed can link targets by right-clicking them, matching the held-card behavior. Terminal-installed cards can also participate in auto connect.
