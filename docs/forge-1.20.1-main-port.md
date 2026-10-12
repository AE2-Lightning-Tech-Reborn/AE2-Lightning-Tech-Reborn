# Forge 1.20.1 GTL upstream port

October 9 selected main/alpha backports and the upgraded GTL dependency are documented in [the follow-up port record](forge-1.20.1-upstream-backports-20261009.md). The pinned Forge baseline and earlier validation below remain historical records.

Reference: `origin/1.20.1` at `877672e`. Incremental review date: 2026-10-08. The original full-tree comparison used `a9037fdc4777beca94d6eb9a169904e43d5de3bc` against GTL `4a854c4` on 2026-10-06, followed by the October 7 port through `f66ea67`. Incremental validation and baseline optimization results are recorded below. This supersedes the earlier selective-port and placeholder-resource status in this document.

## Incremental sync to 877672e (October 8, 2026)

The 15 commits and 99 changed paths from `f66ea67..877672e` are adapted into the existing working tree. The previous local port, GTL planning locks, optional adapters, storage ownership protections and message discriminators are retained.

- Overloaded interface crafting now aggregates demands for the same key, subtracts stock and remaining in-flight output, and applies cooldown after failed plans/submissions. Extraction wakeups use this shared policy after storage caches update.
- Normal item, fluid and Applied Flux I/O share AE2's native wrench direction. Automatic adjacent scanning excludes protected same-grid storage providers, pattern providers and interfaces; an explicit direction permits the player's selected target.
- Matrix controllers and ports gain server-authorized physical pattern migration. Migration leases, source identity checks, cooperative work limits, deferred catalog refreshes, duplicate refunds, persisted recovery custody and cancellation are ported with Forge packets/NBT. Optional ExtendedAE, ExtendedAE Plus and NeoECO adapters remain isolated.
- The overloaded I/O port recipe uses an overload singularity; the Pigmee synthesis station uses diamonds and an anvil. Upstream display names, oriented interface textures/models, button textures, guides and bilingual translations are included.
- Version `2.1.2-beta` and platform-tag extraction are aligned with upstream. Release-type/changelog handling, dependency staging validation and workflow validation are ported.

GTL-specific adaptations:

- Protocol `gtl-10` covers the changed interface synchronization and new migration packets. The original 48 message IDs remain unchanged, with migration action/status messages appended. Clients and servers must update together; upstream's plain protocol `9` is not used.
- The matching Thunderbolt GTL planner-attach build remains `thunderbolt-forge-1.20.1:2.0.0-beta.5`. Upstream's ordinary `thunderbolt-reborn-forge-1.20.1:2.0.3-beta` is not substituted for this dependency. `gtl_build=true` keeps release builds on the configured Maven dependency and preserves the `-gtl` artifact suffix.
- Existing flat client/interface packages are retained through class/path adaptation. The GTL algorithm selector keeps its three states while adopting the upstream settings texture. New optional migration Mixins use early mod-loading guards.
- Existing GTL automatic-export/cache GameTests now set the native block direction, preserving their ownership and reentry assertions.

Java 17/Forge 47.1.47/AE2 15.4.10 validation: all 1,459 JUnit cases and all six adaptive batch stress cases pass, as do 13 Python release-contract cases and both offline Maven repository fixtures (staged originals/mapped artifacts and the GTL no-staged-artifact path). Production and development fixtures compile, `reobfJarJar` succeeds, and the artifact audit verifies all 1,433 resource JSON files, migration classes/Mixins/resources, refmap, notices and the absence of development-only fixtures and duplicate archive entries.

The base migration server profile reports 14 passes; two explicitly skip absent ExtendedAE Plus and NeoECO, leaving 12 exercised cases. The 100,000-slot physical fixture completes with exact item counts over 1,606 observed ticks. Its recorded maximum slice is 1,359,659,700 ns, including host callbacks/class initialization; cooperative budgets do not establish a hard wall-clock bound. The isolated interface-input profile passes all 31 cases, including the new protected-target/native-direction/energy-routing cases.

The default server profile passes all 51 required cases, including the GTL V2 node-less-requester regression. The isolated wireless/I/O profile passes all 46 required cases, including polling, backpressure recovery, conservation and throughput checks, and exits successfully. Both release workflows pass actionlint 1.7.12, and the real Gradle project resolves tag `1.20.1-2.1.2-beta` to version `2.1.2-beta` with the release init script enabled.

With the pinned Forge ExtendedAE Plus 1.5.5 JAR enabled, the first migration profile failed the upstream hybrid-core fixture because that release has no `assembler_matrix_hybrid_plus` or super-matrix classes. The adapter already accepts its physical core through the EAE superclass path. The fixture now verifies the actual `assembler_matrix_pattern_plus` inventory: all 72 physical slots, first/last slot extraction, intact encoded contents, preferred priority, duplicate discovery prevention and deferred catalog refresh. The corrected profile passes all 14 required cases with only the NeoECO case explicitly skipped, so 13 cases are exercised. Newer super-matrix APIs retain their guarded path but are not claimed as runtime-tested by this older JAR.

Current local evidence: `build/port-877672e-junit-stress-package.txt`, `build/port-877672e-migration-gametest.txt`, `build/port-877672e-migration-eaep-adapted-gametest.txt`, `build/port-877672e-interface-gametest.txt`, `build/port-877672e-default-gametest.txt`, `build/port-877672e-wireless-gametest.txt` and `build/port-877672e-artifact-check.json`. The pre-adaptation Plus failure remains in `build/port-877672e-migration-eaep-gametest.txt`. Scratch tools/backups stay under ignored `.codex-tmp/port-877672e`. Client-only migration/pagination/tooltips have compile coverage; no new interactive-client validation is claimed.

Release artifact: `build/libs/ae2lt-forge-1.20.1-gtl-2.1.2-beta.jar` (6,779,483 bytes). SHA-256: `232b52a5452f8eff794594fb0454a5f0bee95cc735695c174cd1f536bc0e0d05`.

## Incremental sync to f66ea67 (October 7, 2026)

All runtime and resource changes from `a9037fd..f66ea67` are adapted to this Forge/GTL project. This is an incremental port, not a replacement of the GTL branch with upstream files. It preserves GTL exclusive planning, the optional registered batch adapters, interface cache/reentry fixes, packet discriminator order and storage ownership boundaries.

| Upstream changes | Adaptation |
| --- | --- |
| `6cf7fbe`, `097cae7` | Native dye base and EHV module textures/models. |
| `af637f4`, `4950f1a` | Native catalyst yields, output-capacity bounds and the conditional Neo ECO recipe. |
| `5ca8d55` | Idle Applied Flux recharge, empty-buffer recovery and 20,000,000-FE chamber buffers. |
| `c0d5f11` | Shared page controls, wheel/right-click navigation and authoritative server pages. |
| `074f457` | EHV beam/HV compensation, rollback and HV/EHV client effects. The wire format changes to `gtl-9`; client and server must update together. Existing message IDs do not move. |
| `fdcf865` | Addon workbench and collector APIs, post-capture event, immutable output snapshots, bounded nonce receipts and indeterminate-dispatch protection. Registered ordinary workbench items also support shift-click. |
| `e4b6a09`, `d27a9e8` | No adjacent Applied Flux distribution in wireless mode; Pigmee virtual-slot IPN swipe protection. Both mixins are optional-mod gated. |
| `287fc0b`, `db94c28` | DifferenceIngredient processor expansion, silicon preference and removal of obsolete pearl/resin shortcuts. |
| `ca80e83` | Remove the global BlockEntity dirty-mark listener; retain bounded cold polling and import backpressure scheduling. FAST cold detection permits the upstream 20-tick interval, plus one observation tick in the fixture. |
| `0e81e92` | Segmented reservoir refills, retained proven tails and bounded rate-reconfiguration calls; regression thresholds are unchanged. |
| `3081a98`, `fe1f74f` | The manual-export receipt fix already present in PR #14 is retained, including reservation-time persistence. |
| `014b750`, `f66ea67` | Adaptive whole-seed loans, minimum loop startup requirements, exact overload pattern quantities/ID-only settings and learned refill/overflow cadence. |

The incremental audit covers 71 changed production-source paths and all 16 resource paths. Package/layout differences, existing GTL safeguards and optional-adapter hooks are retained intentionally. No newly added upstream runtime class or resource remains unported within this pinned delta.

The first wireless regression pass found two local assertions that still required the removed inventory-change notification path. The changed-inventory fixture now measures source drain within 21 ticks and ME delivery within the following five-tick flush interval; the transition fixture uses the upstream 21-tick cold-output bound. Existing conservation, 99% steady-throughput, filter/config wake and backpressure-recovery assertions are not weakened.

Validation uses Java 17, Forge 47.1.47 and AE2 15.4.10 with the local Gradle 8.8 cache. The first addon profile failed five fixture checks because its opt-in registration flag was not passed to the game JVM; those failures are retained as local evidence, not counted as successful validation. The corrected opt-in profile passes all 30 required tests, including collector completion semantics, workbench admission/shift-click, EHV settlement, idle recharge, wireless energy routing, exact quantities and refill cadence. The optional Neo ECO check validates recipe exclusion when the mod is absent, not production with Neo ECO installed.

The final ordinary JUnit pass contains 1,427 tests and the separate adaptive-batch stress pass contains six tests, with no failures, errors or skipped tests. The default server profile passes all 51 required tests. `reobfJarJar` succeeds; the archive retains the refmap and all license/notice files, contains no development-only addon fixtures, and has no duplicate entries. All 1,432 resource JSON files parse. The packaged API classes, `gtl-9` protocol, EHV/page textures and removal of obsolete recipes are checked against the working tree.

| Required GameTest profile | Passed |
| --- | ---: |
| Default server, including the transformed V2 node-less requester regression | 51 |
| Addon API, machine recharge/energy routing, EHV beam and quantity/refill | 30 |
| Isolated wireless/I/O with the polling contract | 46 |
| Isolated interface input, including uncertain manual-export receipts | 23 |
| Total across the four separate profiles | 150 |

Current evidence is local ignored output: `build/port-f66ea67-final-junit-stress-package.txt`, `build/port-f66ea67-default.txt`, `build/port-f66ea67-incremental-gametest-registered.txt`, `build/port-f66ea67-wireless-polling.txt`, `build/port-f66ea67-interface-input.txt` and `build/port-f66ea67-artifact-check.json`. The initial addon flag failure and obsolete wireless-assertion failures remain recorded separately. Release artifact SHA-256: `F76A74E48A44119C738D467352F73E176677695EEC5F18F6B1BE786CB58B0989`.

Incremental reproduction (use the local Gradle executable if the wrapper distribution is unavailable):

```powershell
.\gradlew.bat '-Dnet.minecraftforge.gradle.check.certs=false' test adaptiveBatchStress reobfJarJar --offline --max-workers=1 --no-daemon --console=plain
.\gradlew.bat '-Dnet.minecraftforge.gradle.check.certs=false' runInterfaceIoGameTestServer `
  '-Pae2ltInterfaceIoTestNamespaces=ae2lt_machine_recharge,ae2lt_railgun,ae2lt_addon_api,ae2lt_overload_refill' `
  -Pae2ltAddonApiTests=true -Pae2ltInscriberFixture=true -x downloadAssets --offline --max-workers=1 --no-daemon --console=plain
foreach ($namespace in 'ae2lt_io', 'ae2lt_interface_input') {
  .\gradlew.bat '-Dnet.minecraftforge.gradle.check.certs=false' runInterfaceIoGameTestServer `
    "-Pae2ltInterfaceIoTestNamespaces=$namespace" -Pae2ltInscriberFixture=true `
    -x downloadAssets --offline --max-workers=1 --no-daemon --console=plain
}
```

Client-only pagination, IPN swipe behavior and beam appearance have compile/contract coverage, not a new interactive-client run. Optional integration limitations below still apply. Test fixtures, screenshots, local build evidence and credentials are not release inputs.

## Runtime target

Java 17, Minecraft 1.20.1, Forge 47.1.47, AE2 15, and the project's GTL-compatible Thunderbolt dependency. Forge registry/bootstrap behavior, NBT codecs, array-based pattern outputs, optional-mod boundaries, licenses and attribution are retained. The completed port is prepared as a single summary commit for review; development evidence and scratch files remain local.

## Included behavior

- Crafting allocation, provider dispatch ownership, deferred output return and cancellation recovery, closed-loop seed refill, maintenance snapshots, standalone core budgets, and time-wheel scheduling updates.
- Overloaded interface passive item/fluid input buffering, periodic network flushes, save/reload and dismantling ownership, live filter matching, bounded wireless import polling, indexed exports, bounded barrel transfers, reservoir cadence and transfer energy accounting. Forge capabilities expose the buffered inventories directly and follow invalidation/revival lifecycle.
- Overloaded I/O port, mining factory, manual input-to-output transfer, shared output settings, multiblock recipes, menus, registration, memory-card settings, resource drops, energy accounting and optional integration adapters.
- Overload factory reaction borrowing from the final AdvancedAE recipe manager and derived inscriber recipes. Fifteen obsolete static reaction copies are removed so they cannot shadow edited upstream reactions. Borrowed reactions retain their original viewer category.
- Recipe-defined crystal catalyst fluids and base yields, eightfold matrix output, ordinary output capacity 16,384 and Pigmee output capacity 64. Pigmee catalysts accept water, reuse their catalyst, ignore matrix multipliers, require distinct physical ticks and migrate legacy IDs/yields. Saved jobs are revalidated against current recipe fluids.
- Rainbow Pigmee lightning transformation, preserved item data and block state, the 16-color dye cycle, building panels and slabs, connected textures, recipe unlocks, shaders, Curios integration and real upstream bitmap assets.
- Celestweave shared protection billing, shield/defense consolidation, multidimensional protection, death arbitration, creative echo recovery, phase projection migration, flight control, fluid movement authorization and cache cleanup.
- Railgun EHV beam and percentage charged damage, multidimensional execution compatibility, device-hub controls and synchronization, damage tags, recipes, configuration and guides.
- Tianshu maintenance across wired and wireless terminal families, view selection, large stock synchronization, seed return status, wireless terminal upgrades, quantum bridge support and optional JEI wireless ingredient supply with short-lived server tickets. JEI supply requires JEI and AE2WTLib; the client option is off by default.
- Frequency-card look-at linking uses Forge's live block reach check, validates the hit location and held card on the server, and preserves provider-specific target admission.
- Matching models, screens, textures, blockstates, loot tables, recipes, advancements, tags, bilingual translations and guides. The resource comparison finds no missing upstream resource and no upstream resource change left at its GTL base value. All 1,430 resource JSON files parse and internal AE2LT model/texture references resolve.

## GTL adaptations

- GTLCore planning locks and the V2 / CP-SAT / vanilla exclusive algorithm controls remain authoritative. The V2 engine accepts a node-less requester only when its planning service belongs to the same grid and the grid actively locks V2. Unlocked and cross-grid requests retain the engine's normal rejection.
- GTL crafting menus, wired/wireless Tianshu terminals, CPU status integration, pushed-copy accounting and physical-tick deduplication are preserved. The upstream terminal deletion is not applied to these GTL features.
- Package moves are resolved through imports and existing compatibility facades. Equivalent local implementations, including the ritual burst client handler, need not copy an upstream class name or package layout.
- Optional Useless/NeoECO adapters retain their resolver cache policies. Registered batch adapters take precedence only while present; removing them restores the original resolver. Forge NeoECO versions lacking the allocated FastPath facade use normal dispatch.
- The V2 and batch-provider hot paths reuse one planning decision per request and stable adapter wrappers. Overloaded-interface display and advertised-stock caches now invalidate on configuration, upgrade, node-state and real network I/O changes; passive input guards also invalidate safely after exceptional writes.
- Planning also reuses immutable option/candidate lists, checks CP-SAT registration live, and selects exclusive locks in one node pass without collecting an intermediate list. Service locks retain precedence over owner locks, and equal priorities retain encounter order.
- Passive-input buffering uses primitive long maps and rotates blocked or partially accepted keys in place. The five-tick cadence, 128-key flush budget, round-robin fairness, per-type shared capacity and persisted encounter order are preserved.
- Interface stock exposure reuses primitive aggregation maps and clears their keys after each refresh, including failures. Exact matching skips fuzzy-variant aggregation and simulates only the summed configured cap. Fuzzy views retain saturated shared caps and one copy of each physical variant's stock. Unchanged display keys and amounts reuse their immutable `GenericStack`.
- Advertised-stock cache identity includes the active fuzzy mode. Changing damage matching or restoring settings from NBT within the same physical tick refreshes the view immediately, preserving the cache's fast path while keeping advertised variants consistent with extraction rules.
- Direct proxy transfers invalidate display and advertised-stock caches when an attempted real network mutation exits, including storage or subsequent energy-payment exceptions. Simulation, reentrant rejection and failures before a mutation is attempted retain the existing cache. Exceptions propagate without replaying or undoing an unknown external mutation.
- Interface display queries guard both cached-inventory refresh and simulated extraction, restoring the caller's prior guard state on exit. Reentrant `getStack` and `getAmount` calls return an empty view before accessing display caches, so network callbacks cannot cache an intermediate zero or overwrite another slot's display.
- Normal and wireless automatic item/fluid I/O now run under the same network guard after the separately guarded passive-input flush. Storage or capability callbacks cannot recursively start another I/O pass or insert into the owner's passive buffer. Display and advertised-stock caches invalidate when the automatic pass exits, including exceptions after a storage mutation; interrupted transfers are not replayed.
- Interface network writes use a cached forwarding storage view shared by passive-input flushing, automatic import/export and direct proxy transfers. Every attempted real insertion or extraction invalidates the owning AE2 storage service's inventory snapshot on exit, including failures. Simulation leaves that network snapshot cached. Wrapper reuse requires both the storage-service and delegate identities to match, preventing reuse across a grid or inventory replacement.
- Networking uses GTL's packet order and protocol `gtl-10`, bounded payloads, large-count ingredient codecs and dedicated-server-safe client dispatch. It is deliberately incompatible with the plain upstream protocol.
- `FirmamentConversionRecipe.isSpecial()` remains true for Forge 1.20.1 vanilla recipe-book isolation. Shield payment and death hooks use this version's Forge damage events and Minecraft method signatures. Rainbow rendering retains a fallback when its shader is unavailable.
- AdvancedAE's nested recipe API is extracted/remapped for compilation and tests, without embedding another ae2addonlib in the distributed mod.
- Old overload factory jobs whose removed static recipe ID no longer resolves abort and clear progress before replanning. Inputs, fluid and lightning are consumed at completion, so this revalidation leaves them in place; already spent FE is not refunded.

## Baseline validation history

- 1,330 JUnit cases passed again after the automatic-I/O cache correction, with zero failures, errors or skips. All upstream JUnit class names are represented; imported tests use local package/client-handler adaptations. Batch adapter regressions cover resolver identity/cache policy, registered-adapter priority, rejection fallback, replacement, unregistration and concurrent fallback isolation. The allocation follow-up also covers persisted queue rotation, equal-key merging, restored type capacity, service-lock precedence, equal priorities and live topology/selection changes.
- All 51 required default Forge GameTests passed in the preceding allocation follow-up.
- The preceding full-port/cache baseline passed all 106 required comprehensive Forge GameTests, including wireless I/O and the two interface cache regressions. This profile overlaps the default profile; the counts are not 157 distinct tests. The allocation follow-up reran the 51-test default profile and the 8-test interface-input profile. The exposure follow-up added two interface regressions: its combined 108-test profile passed 106 tests and failed `normalWirelessImportBatchesWithoutBlocking` (blocked output at tick 200) and `fastImport1024Continuous` (infinite cell not mounted at tick 40). Both then passed in an isolated 46-test `ae2lt_io` profile. The latest automatic-I/O correction reran that isolated 46-test profile successfully; the cause of the mixed-profile instability is still unconfirmed and the historical combined failure remains recorded.
- `reobfJarJar` passed after the automatic-I/O cache correction. The final jar is 6,639,190 bytes, contains `ae2lt.refmap.json`, and embeds only MixinExtras. Development-only fixtures, TestRecipeManager, duplicate ae2addonlib and the fifteen removed static reaction recipes are absent. The packaged classes contain the optimized primitive queue, reused planning candidates, primitive exposure aggregation, fuzzy-mode cache key, cache invalidation in both proxy transfer exception handlers, display guards covering inventory refresh and simulated extraction, and the automatic-I/O cache-invalidating storage wrapper.
- All 1,430 resource JSON files and internal AE2LT model/texture references passed the artifact audit. `git diff --check` passed.
- The batch-adapter concurrency regression passed. The latest automatic-I/O follow-up passed all 19 focused Forge interface-input GameTests, including buffering, persistence, energy accounting, same-tick view refresh, unlimited-slot changes, guarded network writes, automatic normal/wireless export and exception cleanup. The exposure regressions verify duplicate-slot shared caps, saturated unlimited caps, caller snapshot isolation, additive counter writes, equal-amount display key replacement, overlapping fuzzy variants, cross-tick scratch clearing and upgrade removal. The damage-mode regression first failed on the previous implementation, then passed after the cache-key fix: `IGNORE_ALL` / `PERCENT_99` transitions and NBT setting restoration within one tick update advertised tool variants consistently with simulated extraction while preserving physical stock and prior caller snapshots. Two mounted-storage regressions first reproduced stale display values after real insertion or extraction mutated storage and threw, then passed after the exit cleanup correction; they also verify advertised amounts, guard recovery, reentry rejection, caller snapshot isolation and successful subsequent transfers without replaying the interrupted operation. Two additional mounted-storage regressions first reproduced another slot retaining a recursive zero within the same tick. After the display-query correction, callbacks from both cached-inventory refresh and simulation return empty nested views, reject nested transfers, run under the guard and leave outer slot amounts and advertised stock correct. New automatic-I/O regressions first reproduced stale stock after normal and wireless export, allowed callback reentry, and left the AE2 cached inventory snapshot stale after direct writes. After the correction, all real automatic and direct writes share a cache-invalidating wrapper, callback reentry is rejected, and exception exits leave subsequent views and transfers usable.
- A Java 17 allocation probe comparing only blocked queue rotation performed 1,280,000 attempts with 1,024 keys: the prior map/iterator loop allocated 90,503,680 bytes, while the primitive in-place loop allocated zero measured bytes. Counts and round-robin order were conserved. This is an isolated allocation measurement, not a whole-server throughput claim. Evidence: `build/optimization-round2-allocations.json`; repeatable probe: `.codex-tmp/PassiveInputAllocationProbe.java`.
- A Java 17 probe of exact interface exposure aggregation refreshed 36 configured slots sharing 18 keys 10,000 times. The previous three-map loop allocated 47,619,264 bytes; the reused primitive-cap loop allocated 640,000 bytes, approximately 98.7% less. Counts were conserved and scratch keys were cleared. This isolates aggregation and excludes network queries, output counters and whole-server throughput. Evidence: `build/optimization-round3-allocations.json`; repeatable probe: `.codex-tmp/InterfaceExposureAllocationProbe.java`.

Current optimization evidence: `build/optimization-full-junit.txt`, `build/optimization-batch-test.txt`, `build/optimization-interface-input-gametest.txt`, `build/optimization-server-matrix-offline.txt`, `build/optimization-reobf.txt` and `build/optimization-artifact-check.json`. The repeatable artifact audit is `.codex-tmp/optimization-artifact-audit.py`. The earlier full-port comparison outputs were cleared with the generated build directory; all evidence and local audit tools remain ignored development output.

Latest allocation evidence: `build/optimization-round2-focused-java17.txt` (18 targeted cases), `build/optimization-round2-background-validation.txt` (full JUnit suite and 51 default server tests), `build/optimization-round2-interface-validation.txt` (8 interface tests and successful packaging), `build/optimization-round2-artifact-check.json` and `build/optimization-round2-allocations.json`.

Latest exposure evidence: `build/optimization-round3-validation.txt` (all 1,330 JUnit cases; combined server profile with 106 passes and two failures), `build/optimization-round3-interface-validation.txt` (all 10 interface tests and successful packaging), `build/optimization-round3-interface-status.json` (exit code 0), `build/optimization-round3-wireless-validation.txt` (all 46 isolated wireless/I/O tests), `build/optimization-round3-wireless-status.json` (exit code 0), `build/optimization-round3-artifact-check.json` and `build/optimization-round3-allocations.json`. The latest artifact audit checks the packaged primitive exposure fields and iteration methods as well as resources and packaging boundaries.

Latest fuzzy-mode evidence: `build/optimization-round4-fuzzy-reproduction.txt` (all 11 interface tests after the fix), `build/optimization-round4-fuzzy-reproduction-status.json` (exit code 0), `build/optimization-round4-validation.txt` (all 1,330 JUnit cases and successful packaging), `build/optimization-round4-status.json` (exit code 0) and `build/optimization-round4-artifact-check.json` (all 1,430 resource JSON files and the packaged fuzzy-mode cache field). The pre-fix real-server regression failed with `same-tick fuzzy mode change retained damaged stock`; the reproduction log was reused for the successful corrected run.

Latest exception evidence: `build/optimization-round5-reproduction.txt` and its status JSON (two pre-fix failures: display remained 32 after insertion increased stock to 40 and extraction decreased it to 24), `build/optimization-round5-validation.txt` and its status JSON (all 1,330 JUnit cases, all 13 interface tests and packaging passed; exit code 0), `build/optimization-round5-artifact-check.json` (all 1,430 resource JSON files) and `build/optimization-round5-proxy-bytecode.txt` (both packaged transfer exception handlers invalidate caches before propagating the exception). Storage mutation exceptions still provide no trustworthy transferred amount for rollback or billing; this correction refreshes views and clears the guard without claiming external transaction recovery.

Latest reentry evidence: `build/optimization-round6-reproduction.txt` and its status JSON (both new tests failed with `recursive zero poisoned another display slot`; exit code 1), `build/optimization-round6-validation.txt` and its status JSON (all 1,330 JUnit cases, all 15 interface tests and packaging passed; exit code 0), `build/optimization-round6-artifact-check.json` (all 1,430 resource JSON files) and `build/optimization-round6-proxy-bytecode.txt` (the guard precedes cached-inventory refresh, restores its prior value on normal and exceptional exit, and returns empty nested display values before cache access). Release SHA-256: `7263BE4FEBA9FDD29E2E44D161F8A22459BA53A1E6B255D32F5A331F08CA13D3`.

Latest automatic-I/O evidence: `build/optimization-round7-reproduction.txt` and its status JSON (the pre-fix run reproduced stale display stock in normal and wireless export plus callback reentry; exit code 1), `build/optimization-round7-guard-only-validation.txt` and its status JSON (guarding the automatic pass alone left the wireless display stale), `build/optimization-round7-validation.txt` and its status JSON (all 1,330 JUnit cases, all 19 interface tests and packaging passed; exit code 0), `build/optimization-round7-wireless.txt` and its status JSON (all 46 isolated wireless/I/O tests passed; exit code 0), `build/optimization-round7-artifact-check.json` (all 1,430 resource JSON files and automatic-I/O wrapper markers) and `build/optimization-round7-proxy-bytecode.txt`. Release SHA-256: `673DA68F5B54788150A9659BC5C6FC20D3E9F6A43B736D9572EA5058CE08786F`.

The combined follow-up completed JUnit and the default server tests before the C: drive filled during development-fixture classpath resolution. The remaining interface tests and packaging then passed using the project-local `build/local-gradle-home` cache and temporary directory on the relocated D: build volume. Validation used Java 17 and one Gradle worker. Dedicated-server validation excludes `downloadAssets`, which fetches client resources and is unnecessary for these fixtures.

The Java comparison reports one upstream filename without a matching local filename: `RitualItemBurstClientBridge`. Its behavior is implemented by `ClientNetworkPacketHandlers.handleRitualItemBurst`, with the imported ritual contract test pointing to that implementation. The 17 classes flagged as unchanged from the GTL base retain exclusive planning, CPU/menu contracts, the dedicated-server packet boundary or compatibility facades whose implementation moved into support packages. The remaining differences are package/formatting adaptations and the explicit GTL differences above; these file-level findings do not represent pending feature ports. The ten differing resource files preserve GTL Mixin targets, terminal guides, renderer model hooks, loot semantics and translations.

The focused profile includes real capability buffering, ownership across save/reload and dismantling, deferred seed returns, protection, quantum bridge updates, borrowed reactions, recipe reloads, catalyst fluids/yields, Pigmee building resources and JEI ingredient supply. The default profile includes the transformed V2 node-less-requester regression, I/O port, mining factory, railgun, terminal, recipe sync, phase and resource-drop behavior.

Forge 1.20.1 filters discovery by the structure template namespace. Development fixtures use their own `empty.nbt` templates; a Gradle guard fails the focused task if no tests actually run. Historical runs reporting zero tests are not counted as validation. Optional integration generators register tests only when their dependencies exist; missing integrations are not counted as successful tests.

Reproduce the server/packaging profiles separately to retain the recorded isolation boundary:

```powershell
.\gradlew.bat '-Dnet.minecraftforge.gradle.check.certs=false' test runGameTestServer -x downloadAssets --max-workers=1 --offline --console=plain
.\gradlew.bat '-Dnet.minecraftforge.gradle.check.certs=false' runInterfaceIoGameTestServer `
  '-Pae2ltInterfaceIoTestNamespaces=ae2lt_interface_input' `
  -Pae2ltInscriberFixture=true reobfJarJar -x downloadAssets --max-workers=1 --offline --console=plain
.\gradlew.bat '-Dnet.minecraftforge.gradle.check.certs=false' runInterfaceIoGameTestServer `
  '-Pae2ltInterfaceIoTestNamespaces=ae2lt_io' -Pae2ltInscriberFixture=true -x downloadAssets --max-workers=1 --offline --console=plain
.\gradlew.bat '-Dnet.minecraftforge.gradle.check.certs=false' runInterfaceIoGameTestServer `
  '-Pae2ltInterfaceIoTestNamespaces=ae2lt,ae2lt_jei_supply,ae2lt_deferred,ae2lt_protection,ae2lt_quantum,ae2lt_overload,ae2lt_catalyzer' `
  -Pae2ltInscriberFixture=true -x downloadAssets --max-workers=1 --offline --console=plain
```

The release artifact is `build/libs/ae2lt-forge-1.20.1-gtl-2.1.0.jar`. Packaging checks require its Mixin refmap and reject development-only `jdb` fixtures, `TestRecipeManager` and duplicate ae2addonlib entries. The existing main-source GameTests are retained. The one-shot ForgeGradle option skips its remote server-connection probe for offline validation; it does not alter dependency resolution or runtime configuration. Local Gradle logs and audit tools remain ignored development output.

## Validation limits

Some existing interface JUnit classes rely on Minecraft bootstrap performed earlier by the full suite. Selecting every `*OverloadedInterface*` class alone exposes that pre-existing initialization-order issue. The full 1,330-case suite and the focused real-server cache tests pass; the batch-adapter concurrency class also passes independently. Buffered-input persistence tests use a private Forge key-type registry and restore the prior registry afterward.

No interactive client session is claimed for shaders, connected textures, JEI transfer variants, maintenance/output settings or railgun controls. A full GTLCore + Thunderbolt order on an actual Transfinite CPU has not been exercised interactively; the V2 engine regression verifies the known rejection path, not the entire pack's crafting workflow.

The exposure follow-up's 108-test mixed server profile had two wireless fixture failures. The latest run passed all 46 wireless/I/O tests and all 19 interface-input tests in separate profiles against the corrected production code; it did not rerun the mixed profile or establish that its historical instability is fixed.

AppGen, KubeJS, Oritech, JustDireThings and AE2CS are absent from this validation runtime. Their guarded integration paths are implemented, but their real machine cycles and script reloads are not runtime-validated. Optional mining-tool integrations likewise require their own installed-mod matrix. This port status is pinned to `877672e`; future upstream commits are outside that comparison. The original full-tree audit used `a9037fd`, the October 7 audit covers the 18 commits through `f66ea67`, and the October 8 audit covers the following 15 commits.
