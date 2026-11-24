---
navigation:
  title: Overloaded Frequency Card
  icon: ae2lt:overloaded_frequency_card
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_frequency_card
---

# Overloaded Frequency Card

<ItemImage id="ae2lt:overloaded_frequency_card" scale="2" float="left" />

The **Overloaded Frequency Card** pairs with an **Advanced Wireless Overloaded Controller** to connect remote device clusters directly to your ME network without running cables or placing Wireless Receivers.

## Binding & Basic Controls

* **Bind Frequency**: Sneak-right-click an **Advanced Wireless Overloaded Controller** to bind the card to its frequency (bound cards are used by their binder).
* **Open Card UI**: Right-click air to open the card's frequency and auto-connect settings.
* **Clear Binding**: Sneak-right-click air to clear the frequency binding while **preserving** auto-connect toggles.
* **Shortcuts**:
  * Hold `Shift` and scroll, or press <KeyBind id="key.ae2lt.toggle_frequency_card_auto_connect" />, to toggle **ME Auto-Connect** (this shortcut only toggles ME auto-connect, not FE).
  * Press <KeyBind id="key.ae2lt.toggle_frequency_card_link" /> to toggle the link on the block you are looking at.
  * If carrying multiple equal-priority cards causes ambiguity, hold the specific card you intend to use.

<OptionalSection modId="ae2wtlib">
* **AE2WTLib Support**: Install the card inside a compatible wireless terminal's upgrade slot and open the card UI from the terminal toolbar.
</OptionalSection>

<OptionalSection modId="curios">
* **Curios Support**: Equipping the card in its dedicated Curios slot enables hotkeys and placement auto-connect without taking a hotbar slot.
</OptionalSection>

## Wireless ME Cluster Links

* **Cluster Linking**: Right-click a compatible ME node to toggle a wireless ME link for that physically connected device cluster.
* **Merging & Splitting**: Connected clusters on the same frequency merge automatically; splitting a cluster keeps wireless links on the resulting sub-clusters. Physically bridging two clusters bound to **different frequencies** pauses their wireless links until separated again.
* **Auto-Resume**: Pending links automatically reconnect once the Advanced Wireless Overloaded Controller's chunk reloads and comes online.
* **Special Blocks**: Controllers and unformed multiblocks without an active grid node cannot be linked until valid.

## Auto-Connect Options

**ME Auto-Connect** and **FE Auto-Connect** are independent toggles in the card UI:

* With **ME Auto-Connect** enabled, placing compatible ME blocks automatically links them to the bound advanced frequency.

<OptionalSection modId="appflux">
## Wireless FE Power (Applied Flux)

With Applied Flux installed and FE stored in your ME network, the Overloaded Frequency Card can also supply remote (cross-dimensional) wireless FE (distinct from the same-dimension [Overloaded Power Supply](overloaded-power-supply.md)):

* **Normal Right-Click**: Attempts both ME linking and FE linking; checks the clicked face for FE first, then remaining faces. Device FE admission follows block-specific rules (receiving FE on a machine is distinct from a provider outputting FE to adjacent machines).
* **Ctrl + Right-Click Bulk FE**: Toggles wireless FE across 6-way connected identical machines in loaded chunks, capped at **8,192** targets per operation (exceeding the cap cancels during discovery; interruptions keep already applied changes).
* **FE Auto-Connect**: Newly placed FE-capable machines run a placement FE check (full buffers confirm via stored capacity); if automatic validation skips a machine, right-click it manually. Bound FE endpoints stay idle when full and resume automatically when power is needed.
</OptionalSection>

## Troubleshooting

If a link fails to establish, check in order:

1. The transmitter is an **Advanced Wireless Overloaded Controller**, chunk-loaded, online, and conflict-free.
2. The target multiblock is formed, exposes an active ME node, and you hold permission on the frequency.
3. For wireless FE, verify Applied Flux is installed, the target face accepts FE, and the ME network has stored FE available.
