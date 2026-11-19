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

The Wireless Network system lets you extend your Overloaded ME network across long distances — or even across dimensions — without running cables. A **Wireless Overloaded Controller** broadcasts on a frequency, and one or more **Wireless Receivers** tune in to create a virtual grid connection.

## Components

### Wireless Overloaded Controller

<Row>
  <BlockImage id="ae2lt:wireless_overloaded_controller" scale="4" />
</Row>

The **Wireless Overloaded Controller** is an [Overloaded ME Controller](overloaded-controller.md) that doubles as a wireless transmitter. It broadcasts its grid node on a selected frequency so that remote Wireless Receivers can connect to it.

* Supplies the same extra channels and energy as a regular Overloaded Controller
* One Wireless or Advanced Wireless Overloaded Controller is enough for the whole Overloaded Controller multiblock; the other controller blocks can stay regular Overloaded ME Controllers
* Each controller can only broadcast on **one frequency** at a time
* A frequency can only be occupied by one transmitter at a time
* Same-dimension only; each receiver link has a limited channel capacity (**32 channels** in the default AE2 channel mode)

### Advanced Wireless Overloaded Controller

<Row>
  <BlockImage id="ae2lt:advanced_wireless_overloaded_controller" scale="4" />
</Row>

The **Advanced Wireless Overloaded Controller** provides the following features:

* **Cross-dimension support**: Wireless Receivers in other dimensions can connect to it
* **No per-receiver channel limit**: the network remains subject to its AE2 channel mode and controller capacity
* **Frequency-card links**: connect ME devices and parts directly with an [Overloaded Frequency Card](overloaded-frequency-card.md)
* **Wireless FE power**: with Applied Flux installed, supply compatible machines from FE stored in the network
* **Network management**: inspect ME devices, FE power records, and frequency connections, with remote actions for supported devices

### Wireless Receiver

<Row>
  <BlockImage id="ae2lt:wireless_receiver" scale="4" />
</Row>

The **Wireless Receiver** is the counterpart that connects to a remote Wireless Overloaded Controller. Place it anywhere in the world, set it to the same frequency as the controller, and a virtual grid connection is established automatically.

* Consumes 5 AE/t idle power
* Can only connect to one frequency at a time
* Cross-dimension connections require an **Advanced** Wireless Overloaded Controller on the transmitter side

## Setting Up

1. Place or replace one block in the Overloaded Controller multiblock with a **Wireless Overloaded Controller**, then right-click it to open the frequency GUI
2. Select or create a frequency
3. Place a **Wireless Receiver** at the remote location and right-click it to open its frequency GUI
4. Select the same frequency — the receiver will automatically establish a virtual connection to the controller's grid

Once connected, the receiver acts as if it were physically cabled to the controller. Devices attached to the receiver's local network gain access to the controller's ME network, including channels, storage, and crafting.

## Frequency Screen

The Advanced Wireless Overloaded Controller and Overloaded Frequency Card share the same top navigation. Hover over an icon to display its page name.

| Page | Purpose |
|------|---------|
| Overview | Inspect the current frequency, connection status, access level, and network channels; cards also provide auto-connect switches |
| Frequency | Search and bind an existing frequency, create a frequency, or disconnect the current binding |
| Members | Inspect membership and change roles or remove members where permitted |
| Network management | Switch between the ME devices, FE power, and Frequency connections views |
| Frequency settings | Edit the frequency name, color, and security settings; owners can delete the frequency |

Settings and member actions depend on the player's role. Administrators can edit names and colors and manage lower-ranking members. Changing security, passwords, owner or administrator roles, and deleting frequencies requires owner access. Deleting a frequency is irreversible and removes its associated wireless connections. Use **Disconnect** when changing only the frequency of the current device or card, rather than deleting the frequency.

### Network Management

* **ME devices** lists network devices and their operating states. Select a device to locate it or open its native screen remotely. The remote screen provides a return button for network management.
* **FE power** lists power paths, sources, targets, and actual delivery rates. Records include frequency wireless power and other supported power methods. **Unbind wireless endpoint** removes only the selected frequency's wireless power connection, preserving ME links.
* **Frequency connections** lists wireless channel connection points and their loaded state for inspecting receiver and frequency-card links.

Lists support search, sorting, and grouping by device type. Search by name, type, mod ID, dimension, or coordinates, separating multiple terms with spaces. Expand a group to select an individual device. **Locate** displays coordinates and highlights a locatable device for 15 seconds; click the coordinate message to copy it.

The FE power view offers 1t, 1s, 5s, 30s, and 1min measurement windows. All values are average actual output in **FE/t**, including periods with no output. They do not represent stored energy or a device's rated input limit.

Remote actions require an available target and sufficient permissions. Supported native screens can be opened across dimensions, but network management does not force-load target chunks. Devices without remote-menu support cannot be configured this way. A frequency card requires an available advanced wireless transmitter before network management can be opened.

## Frequency Security

Frequencies support access control:

| Level | Behavior |
|------|----------|
| Public | Anyone can bind a receiver to this frequency |
| Private | Only the frequency owner and allowed members can bind |
| Encrypted | Requires a password to bind |

Unbound controllers and receivers are always accessible regardless of security level.

## Tips

* Use the Advanced variant when you need to bridge networks across dimensions
* Use the Advanced variant when one receiver must serve more channels than the ordinary wireless link allows
* A single controller can serve many receivers — each receiver creates its own virtual connection
* You do not need to replace every block in the multiblock with a wireless variant; add more wireless controllers only when you intentionally want extra independent transmitter frequencies
