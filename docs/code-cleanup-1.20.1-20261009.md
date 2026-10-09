# Forge 1.20.1 code cleanup

Branch: `1.20.1-alpha`. Source and test code are reduced by 3,142 lines.

Removed 28 obsolete internal classes after checking production/test references,
reflective names, resource registration and existing replacements. This includes
the old controller lookup, ME insertion inventory, unused EJECT SavedData,
unregistered cell handler and renderers, unused module registry/capability
collector, and unused matrix and terminal prototypes. The live registry,
inventories, plugin entry points and public API package remain in place.

Matrix tests now exercise `MatrixCraftingCluster` and `MatrixPatternRepository`
directly. Ritual assertions inspect the active client packet handler. Only tests
for removed prototype code were dropped. Permission, cell persistence and cache
comments retain their behavioral constraints without development history or
decorative sections.

The additional Forge-local batch admission interface had no production provider.
Its candidate accessor, capacity/push Mixins, state map and artificial admission
tests are removed. The scoped input allocator, rollback, shared-seed accounting,
ordinary fallback and FE return fixes remain. This port does not expose newer
Thunderbolt prepared admission; the port notes have been corrected accordingly.

## Validation

Java 17, Forge 47.1.47, AE2 15.4.10 and Thunderbolt 2.0.3-beta:

- Clean build, followed by final rebuilds including `reobfJarJar`.
- 1,407 unit tests and six adaptive batch stress tests passed without skips.
- 46 required Forge GameTests passed in a new world.
- Twelve release metadata tests and the Forge release repository resolution test passed.
- The release JAR contains Java 17 classes, valid JSON, Mixin classes/refmap and
  bundled MixinExtras. Removed classes and development FE fixtures are absent;
  metadata, licenses and attribution are retained.
- A token comparison confirms comment-only edits do not alter Java statements.

Reproduce the build with `./gradlew clean test adaptiveBatchStress build`.
Native tests: `./gradlew -I scripts/provider-energy-output-test.init.gradle
-PproviderEnergyTestDirectory=build/cleanup-native-new runGameTestServer`.

This cleanup establishes functional regressions and smaller source code; it does
not measure server MSPT gains. A separate production server and live client
rendering comparison were not run for this change.
