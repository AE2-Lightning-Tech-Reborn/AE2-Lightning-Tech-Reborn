---
navigation:
  title: Overload Devices
  icon: ae2lt:overload_device_workbench
  parent: index.md
  position: 55
item_ids:
  - ae2lt:overload_device_workbench
---

# Overload Devices & Workbench

<ItemImage id="ae2lt:overload_device_workbench" scale="2" float="left" />

The **Overload Device Workbench** is the assembly, network-binding, and module-management station for [Celestweave](celestweave.md) armor and the [Electromagnetic Railgun](electromagnetic-railgun.md).

<SubPages />

## Assembly & Network Binding

1. **Connect to ME**: Place the **Overload Device Workbench** on the ME network that will supply Lightning (and optionally FE), and keep its chunk loaded.
2. **Bind Device**: Inserting a Celestweave piece or Electromagnetic Railgun records the workbench's dimension and coordinates as the device's network anchor. Runtime network access resolves through this anchor, so the anchor chunk and grid must stay online.
3. **Install Core**: Insert 1 <ItemLink id="ae2lt:ultimate_overload_core" /> into the core slot—required before armor modules or railgun firing can operate.
4. **Install & Uninstall Modules**:
   * Modules placed in the module input slot are validated against target slot, stack limit, and mutually exclusive groups, installing at **1 module per 20 ticks (1 second)**.
   * In the installed-modules list, **click** to uninstall 1 module or **`Shift + Click`** to uninstall the entire stack.

## Recharging

Each Overload Device carries its own internal FE buffer and can be charged directly in external FE chargers.

<OptionalSection modId="appflux">
With Applied Flux installed, network-bound devices can also recharge automatically from FE stored in their bound ME network.
</OptionalSection>

## Overload Device Configuration UI

Press **Open Overload Device Config** (current binding: <KeyBind id="key.ae2lt.open_config" />, default `G`) to open the **Overload Device** menu, featuring **Helmet**, **Chest**, **Legs**, **Boots**, and **Railgun** tabs to toggle modules and adjust behavior.

### Troubleshooting Order

If a module or weapon mode does not activate, verify in order:

1. **Equipped & Enabled**: The piece is equipped (or held in mainhand) and the module is enabled in the **Overload Device** UI.
2. **Core Installed**: An **Ultimate Overload Core** is installed via the Overload Device Workbench.
3. **Bound Network**: The bound workbench chunk is loaded and the ME network is online with required Lightning available.
4. **Energy**: The device has sufficient FE in its buffer.
