# Forge 1.20.1 alpha port

Version: `2.1.2-alpha.1`.

This port starts from the Forge `1.20.1` branch at
[`877672ef`](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/commit/877672ef11cb49efd5753d49979e48c406458090)
and brings its remaining differences up to the upstream `alpha` snapshot
[`b25f89bf`](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/commit/b25f89bf29fc2c1005eacf5b0535d4f4b0665dd4).
Features already present in the Forge baseline remain on their Forge implementation.

## Changes

- Pattern providers refuse passive Applied Flux FE returns in every return mode,
  including unfiltered AUTO recovery. Induction cards still deliver energy. FE
  already owned by a saved return buffer still drains into ME storage.
- Crystal catalyzer JEI/EMI displays use the alpha layout and tooltips. EMI uses
  the recipe's actual fluid ingredient, including non-water recipes.
- Mining factory and overloaded I/O port guides and related English/Chinese
  catalyzer text match the alpha snapshot.
- Ordinary single-copy fallback keeps a provider available after partial batch
  success when that provider is no longer busy.

## Forge adaptation

The runtime remains Java 17, Minecraft 1.20.1 and Forge. Recipe, inventory and
capability APIs use their Forge equivalents. The minimum Forge version is
47.1.47 and the Thunderbolt dispatcher API was verified with 2.0.3-beta; the
declared compatible dependency range is `[2.0.3-beta,2.1.0)`.

Time-wheel batches retain the existing LT-scoped input allocator and native
Thunderbolt dispatch. The test-only admission contract was removed because no
Forge production provider implemented it. Concrete-input prepared admission
from newer Thunderbolt APIs is not included in this Forge port.

The repository license and existing project attribution are retained. No
Thunderbolt classes or modified dependency JARs are bundled by this port.

## Validation

See [the cleanup validation report](code-cleanup-1.20.1-20261009.md) for the
current unit, stress, native Forge and release artifact checks and their limits.

Reproduce the native regression run with:

```sh
./gradlew -I scripts/provider-energy-output-test.init.gradle -PproviderEnergyTestDirectory=build/provider-energy-clean runGameTestServer
```
