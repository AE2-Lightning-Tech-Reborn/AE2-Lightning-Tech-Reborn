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
- Time-wheel batch extraction admits a batch after reserving one concrete input
  prototype and before taking the remaining materials. Rejection and admission
  exceptions return that prototype; sibling task allocations remain protected.
- Ordinary single-copy fallback keeps a provider available after partial batch
  success when that provider is no longer busy.

## Forge adaptation

The runtime remains Java 17, Minecraft 1.20.1 and Forge. Recipe, inventory and
capability APIs use their Forge equivalents. The minimum Forge version is
47.1.47 and the Thunderbolt dispatcher API was verified with 2.0.3-beta; the
declared compatible dependency range is `[2.0.3-beta,2.1.0)`.

Forge Thunderbolt does not expose the newer `PreparedBatch`/capacity limiter
API. `TimeWheelBatchAdmission` provides the optional contract in AE2LT's own
package. The existing dispatcher still selects, balances and accounts for
providers; an LT-scoped Mixin supplies admission and prepared submission only
for the matching time-wheel task and inventory. Existing Forge providers retain
their advisory capacity and ordinary submission behavior. A provider must
implement the optional admission contract to report a tighter concrete-input
limit. Other CPU paths keep native extraction and submission behavior.

The admission contract is adapted from
[Thunderbolt-Core-Reborn](https://github.com/AE2-Lightning-Tech-Reborn/Thunderbolt-Core-Reborn),
by the AE2-Lightning-Tech-Reborn contributors, under GNU LGPL 3.0. The repository
license and existing project attribution are retained. No Thunderbolt classes
or modified dependency JARs are bundled by this port.

## Validation

- Full Java 17 compilation, 1,419 unit tests, and six adaptive batch stress tests.
- Forge 47.1.47 with AE2 15.4.10, Thunderbolt 2.0.3-beta and Applied Flux:
  47 required GameTests passed, including FE return rejection, historical buffer
  recovery, shared-seed batches, and prepared admission through the actual Mixin.
- Admission tests reserve only one prototype from 10,000 inputs before limiting
  extraction to 64; strict sibling stock stays intact. Zero/throwing admission,
  partial dispatch, missing-input retry and scope isolation preserve materials.
- Twelve release metadata tests and the Forge release repository resolution test.
- `reobfJarJar` builds the distributable. Its Forge metadata, Java 17 bytecode,
  Mixin/refmap, bundled MixinExtras and JSON resources are checked; development
  FE GameTest fixtures are excluded.

These checks establish functional behavior and bounded extraction. They do not
measure overall server MSPT improvements or cover every optional-mod/client
combination. JEI/EMI presentation was compiled and its resources checked; a live
client rendering comparison was not performed. A standalone production server
startup was not completed because the Forge installer download was rejected or
truncated; production validation is limited to the reobfuscated artifact checks.

Reproduce the native regression run with:

```sh
./gradlew -I scripts/provider-energy-output-test.init.gradle -PproviderEnergyTestDirectory=build/provider-energy-clean runGameTestServer
```
