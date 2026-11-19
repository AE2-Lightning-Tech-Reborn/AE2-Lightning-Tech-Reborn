# JEI 1.21.1 compatibility

LT supports the legacy transfer path and the context-based JEI transfer path without changing the installed JEI version. Keep behavior in the public JEI API where possible; reserve mixins for capabilities the API cannot provide.

## API integration

- `TianshuPatternTransferHandler` decorates only LT's registered wired/wireless universal transfer handlers. It owns recipe metadata capture, closed-loop discovery, and successful Alt encoding. It replaces the global mixin into AE2 JEI Integration's handler.
- On alpha, the same public handler provides alias-source selection and cosmetic button feedback. No private transfer-button fields or alias-selection injections are needed.
- `JeiTransferContextBridge` uses the public `IRecipeTransferContext` interface. Its API lookup is lazy because JEI before 19.52 does not contain this interface. Partial refill delegates all context methods except the requested slots/maximum, including the original transfer ID and completion callback. No mixin into the concrete context class or concrete copy factory is needed.

## Remaining mixins

- Transfer button: the universal handler API does not provide the displayed category or a way to suppress the successful-transfer close callback. Static `WrapOperation` handlers wrap the original calls, with `try/finally` metadata scope and restoration for nested transfers. These handlers do not read local slot 0 or access private fields. They cover both `onPress(IJeiUserInput)` and the newer `transferRecipe(boolean, boolean)` entry point. Simulation remains under JEI's control, and pinned callbacks still run outside the recipe screen.
- Wireless supply: a global wrapper is necessary to refill arbitrary other mods' existing transfer handlers without replacing their registrations. Only the legacy or modern hook is enabled, with the switch at JEI 19.52.
- Multiblock layout: JEI's public category API does not expose the actual available recipe-panel area or its cached layouts. Two explicit method descriptors support the legacy height/padding arguments and the newer `ImmutableSize2i` argument. The hook runs only at method entry and resizes only LT's structure category.

Do not inject instance callbacks at arbitrary returns of optimized JEI code. The published 19.70.0.464 `onPress` reuses local slot 0 for a boolean; injecting an instance callback there produces an invalid `aload_0`. Changing `require` to zero does not make an already-matched callback safe. Likewise, do not select an overloaded method by name alone when its argument types changed.

## Regression verification

The unchanged official NeoForge JEI jars tested with the real Mixin transformer are:

| JEI | Transfer path | Button, wireless supply and multiblock layout |
| --- | --- | --- |
| 19.27.0.336 | Legacy | Passed |
| 19.51.0.420 | Legacy | Passed |
| 19.52.0.421 | Context | Passed |
| 19.57.0.450 | Context | Passed |
| 19.64.0.455 | Context | Passed |
| 19.70.0.460 | Context | Passed |
| 19.70.0.464 | Context | Passed |

Both main and alpha are checked against this matrix. The harness verifies the transformed methods, checks that the required hooks actually matched exactly once, exercises exception/nested metadata cleanup, and verifies public-context forwarding. The original alpha button mixin fails the 19.70.0.464 bytecode control with `Expected an object reference, but found I`.

Run the reproducible matrix with Java 21:

```sh
./gradlew --no-configuration-cache \
  -I scripts/jei-compatibility-test.init.gradle \
  -PjeiCompatJars=/path/to/old-jei.jar:/path/to/new-jei.jar \
  jeiCompatibilityTest
```

The focused client probe is opt-in and excluded from production jars. Seed a disposable `build/jei-compat-client/saves/JeiQA` world and test options, then run:

```sh
./gradlew --no-configuration-cache \
  -I scripts/jei-compatibility-client-test.init.gradle \
  -Pneo_version=21.1.262 \
  -PjeiCompatJar=/path/to/jei.jar runClient
```

Client verification uses Minecraft 1.21.1 / NeoForge 21.1.262. Alpha is tested with JEI 19.27.0.336 and 19.70.0.464; main is tested with 19.51.0.420 (legacy transfer) and 19.70.0.464. Main’s existing required LDLib2 2.2.42 rejects JEI below 19.51.0.417 during mod loading, independently of LT’s hooks; the 19.27 row above is bytecode verification, not a claim that main can launch below that dependency floor. Main covers crafting/smelting/structure browsing, simulated versus actual transfer, metadata lifetime, return to the terminal, and the server's received inputs/outputs. Alpha additionally covers real registered-category source selection, wired/wireless terminal variants, machine IDs, alias management and automatic uploads. This is a focused mod set, not a full modpack compatibility claim; other mods' JEI mixins remain independent.
