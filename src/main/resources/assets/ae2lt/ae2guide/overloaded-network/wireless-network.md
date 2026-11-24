---
navigation:
  title: Wireless Network
  icon: ae2lt:wireless_overloaded_controller
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:wireless_receiver
  - ae2lt:wireless_overloaded_controller
  - ae2lt:advanced_wireless_overloaded_controller
---

# Wireless Network

<ItemGrid>
  <ItemIcon id="ae2lt:wireless_overloaded_controller" />
  <ItemIcon id="ae2lt:advanced_wireless_overloaded_controller" />
  <ItemIcon id="ae2lt:wireless_receiver" />
</ItemGrid>

The wireless network system provides both **remote ME grid bridging** and **wireless terminal coverage** using two wireless controller tiers (both inheriting Overloaded ME Controller multiblock behavior) and the Wireless Receiver.

## Wireless Controller Comparison

| Feature | Wireless Overloaded Controller | Advanced Wireless Overloaded Controller |
|---------|--------------------------------|-----------------------------------------|
| Remote ME Grid Range | Same dimension | Cross-dimension |
| Channels per Wireless Receiver Link | Default **32 channels** per link (scales with AE2 channel multiplier) | High-capacity wireless link |
| Wireless Terminal Coverage | Full dimension of the controller | Cross-dimensional |
| Frequency Card Direct Linking & Remote FE | Not supported | Supported (with [Overloaded Frequency Card](overloaded-frequency-card.md)) |

## Remote ME Grid Bridging

1. Place a Wireless or Advanced Wireless Overloaded Controller in your controller multiblock (one transmitter represents the entire controller multiblock).
2. Right-click it to open the frequency UI, create a frequency, and bind it. **Only one transmitter may hold a given frequency at a time.**
3. Place a **Wireless Receiver** (5 AE/t idle drain, binds one frequency at a time) at your remote site and bind it to the same frequency.
4. The transmitter must stay chunk-loaded, online, and free of network conflicts or boot states for remote links to activate.

## Wireless Terminal Coverage

Terminal coverage does not require broadcasting a frequency:

* Bind your wireless terminal directly to a Wireless / Advanced Wireless Overloaded Controller (or to an AE2 Wireless Access Point on the same network) to gain same-dimension or cross-dimension coverage.
* Requires the network, binding anchor, and covering controller to stay online; terminal power consumption is calculated at the equivalent base range of a vanilla AE2 Wireless Access Point.

## Frequency UI & Security

The frequency interface exposes five management pages:

* **Overview**: Displays active connection and channel summaries.
* **Frequencies**: Search, create, bind, or **Disconnect** (use Disconnect for normal unbinding).
* **Members**: Manage Owner, Admin, and Member roles.
* **Network Management**: Split into **ME Devices**, **FE Power**, and **Frequency Links**:
  * **ME Devices**: Locate devices or remotely open supported native GUIs (subject to permissions, chunk loading, and target capabilities).
  * **FE Power**: Displays average real transfer rate (FE/t, including idle time) over 1t, 1s, 5s, 30s, and 1min windows; unbinding here only removes wireless FE links.
  * **Frequency Links**: Inspects Wireless Receiver and Frequency Card connections and loaded status.
* **Settings**: Edit name, color, security level, password, or **Delete** (Owner only; deletes all links on that frequency).

### Security Levels

* **Public**: Anyone may bind; non-members are automatically registered as regular members on their first successful bind.
* **Private**: Restricted strictly to listed members.
* **Encrypted**: Authorized members bind directly **without re-entering the password**; new users who enter the correct password join and are registered as regular members.
