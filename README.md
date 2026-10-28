# AE2 Lightning Tech — Forge 1.20.1 Port

[中文文档](README_zh_CN.md)

An [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) addon that introduces a lightning energy system, advanced machines, and overloaded network components.

> GTO build · Minimum supported pack: GTO 0.6.0-dev-23 · Minecraft 1.20.1 / Java 21 / Forge 47.4.20+

The `gto` branch maintains the Forge 1.20.1 GTO adaptation; the ordinary `1.20.1` branch provides same-version updates for comparison. The current LT version is `2.1.2-gto.12`. See [GTO compatibility notes](GTO_COMPATIBILITY.md) for the complete dependency set and validation limits.

## Features

- Two lightning-energy tiers: High Voltage and Extreme High Voltage.
- Lightning Collector, Atmospheric Ionizer and Tesla Coil energy production.
- Lightning Assembly Chamber, Lightning Simulation Room, Overload Processing Factory and Crystal Catalyzer processing chains.
- Overload crystal growth, decay and bulk-processing progression.
- High-throughput overloaded ME controllers, interfaces, pattern providers and cables.
- Wireless pattern routing, Tianshu supercomputer automation and configurable batch dispatch.
- Celestweave modular armor and the electromagnetic railgun system.

## Dependencies

| Mod | Requirement |
|-----|-------------|
| Minecraft 1.20.1 | Required |
| Java 21 / Forge 47.4.20+ | Required |
| GTO pack 0.6.0-dev-23 or dev-25 | Verified server environments; use each pack's matching dependencies |
| GTOCore 26.10.10 or 26.10.18 | Required; loader range `[26.10.10,26.10.11),[26.10.18]` |
| AE2-GTO 15.2610.31 or 15.2610.40 | Required; the two exact versions are accepted |
| Thunderbolt Core Reborn 2.0.3-gto.6 | Required, exact version |
| NeoECO GTO 20.5.0-gto.1 or 20.5.2-gto.1 | Optional; the two exact versions are accepted |
| JEI or EMI, Jade | Optional integration |
| AdvancedAE, ExtendedAE, Applied Flux, AE2WTLib | Optional integration |
| Mekanism, Curios, Flux Networks, Polymorph | Optional integration |

Optional integrations are isolated and are not required for normal startup.

Update clients and servers together. GTOCore 26.10.5, AE2 15.2610.8 and Core 2.0.3-gto.1 are incompatible with the current LT release, as are ordinary AE2 and NeoECO editions. The minimum pack version does not imply support for arbitrary newer dependencies.

## Build

Use Java 21, prepare the matching Core using the [GTO build instructions](GTO_COMPATIBILITY.md#构建), then run:

```powershell
.\gradlew.bat test build
```

The distributable artifact is `build/libs/ae2lt-forge-1.20.1-2.1.2-gto.12.jar`.
The `-slim.jar` artifact is an intermediate development artifact and must not be distributed.

## Public API

`com.moakiee.ae2lt.api.*` is the stable surface for addon authors. It includes lightning-energy capabilities and events, frozen block-entity and recipe IDs, and the server-authoritative wireless-frequency API. Forge event subscribers should use `MinecraftForge.EVENT_BUS`.

Everything outside `com.moakiee.ae2lt.api.*` is internal and may change between minor versions.

## License

- Source code: [GNU LGPL 3.0](LICENSE)
- Textures and visual assets: [CC BY-NC-SA 3.0](LICENSE_ASSETS.md)
- Adapted or bundled code: [Third-party notices](THIRD_PARTY_NOTICES.md)

Developed by **MOAKIEE**, **CystrySU**, **gjmhmm8**, **_leng**, **TedXenon**, and **MHanHanBing**.
