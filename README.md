# AE2 Lightning Tech — Forge 1.19.2 Port

[中文文档](README_zh_CN.md)

An [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) addon that introduces a lightning energy system, advanced machines, and overloaded network components.

> Requires AE2 and Thunderbolt Core Reborn · Built for Minecraft 1.19.2 / Forge 43.5.2 (43.x)

This branch is the maintained Forge 1.19.2 port. The primary project targets newer Minecraft and NeoForge versions; behavior changes are ported back where the 1.19.2 APIs support them.

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
| Minecraft 1.19.2 | Required |
| Forge 43.5.2 (43.x) | Required |
| Applied Energistics 2 12.9.12 (12.x) | Required |
| Thunderbolt Core Reborn 2.0.3-beta3 to `<2.1.0` | Required |
| JEI or EMI, Jade | Optional integration |
| Extended Pattern Provider, Applied Flux Reborn, AE2WTLib | Optional integration |
| Mekanism, Curios, Flux Networks, Polymorph | Optional integration |

Optional integrations are isolated and are not required for normal startup.

## Build

Use Java 17 and run:

Build Thunderbolt Core Reborn and Applied Flux Reborn from their `1.19.2`
branches first. The default local TB location is the sibling repository's
`build/libs`; specify `-Pthunderbolt_build_libs=/path/to/build/libs` if needed.

```sh
./gradlew test build -Pappflux_build_libs=/path/to/Applied-Flux-Reborn/build/libs
./gradlew runGameTestServer -Pappflux_build_libs=/path/to/Applied-Flux-Reborn/build/libs
```

The distributable artifact is `build/libs/ae2lt-forge-1.19.2-2.1.2-beta2.jar`.
The `-slim.jar` artifact is an intermediate development artifact and must not be distributed.

## Public API

`com.moakiee.ae2lt.api.*` is the stable surface for addon authors. It includes lightning-energy capabilities and events, frozen block-entity and recipe IDs, and the server-authoritative wireless-frequency API. Forge event subscribers should use `MinecraftForge.EVENT_BUS`.

Everything outside `com.moakiee.ae2lt.api.*` is internal and may change between minor versions.

## License

- Source code: [GNU LGPL 3.0](LICENSE)
- Textures and visual assets: [CC BY-NC-SA 3.0](LICENSE_ASSETS.md)
- Adapted or bundled code: [Third-party notices](THIRD_PARTY_NOTICES.md)

Developed by **MOAKIEE**, **CystrySU**, **gjmhmm8**, **_leng**, **TedXenon**, and **MHanHanBing**.

## 1.19.2 compatibility

- Applied Flux remains a separate optional mod. FE cells, the overloaded power
  supply and induction-card integration use Applied Flux Reborn `1.19.2-1.3.7-reborn.1`.
- Validated client baseline: JEI `11.68.0.1087`, Jade `8.9.2`, Curios
  `1.19.2-5.1.6.4`, AE2WTLib `12.9.7-forge`, with strict Mixin target auditing.
- AdvancedAE-specific reaction recipes/screens and AE2 15's native EMI encoding
  handlers are unavailable on AE2 12. Custom EMI displays remain; JEI is the
  validated recipe-transfer path. Veil rendering uses the native shader fallback.
- Recipes reference AE2 12's quartz growth accelerator and vibration chamber;
  this AE2 version has no crystal resonance generator.
- GameTests include all registered machine constructors, blockstate orientation,
  overloaded-interface final-slot configuration, 40-device channel allocation,
  closed-loop crafting, status packets, FE drops and Celestweave damage semantics.

See [third-party notices](THIRD_PARTY_NOTICES.md) for the AE2-derived compatibility code.
