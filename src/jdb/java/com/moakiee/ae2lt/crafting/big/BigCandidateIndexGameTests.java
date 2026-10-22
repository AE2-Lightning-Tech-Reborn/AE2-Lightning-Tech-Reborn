package com.moakiee.ae2lt.crafting.big;

import appeng.api.config.Actionable;
import appeng.api.crafting.*;
import appeng.api.networking.crafting.*;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.crafting.runtime.api.DeferredCraftingProvider;
import com.moakiee.thunderbolt.core.crafting.big.BigExecutionProgram;
import com.moakiee.thunderbolt.core.crafting.support.CraftingProviderRevision;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.*;

@GameTestHolder("ae2lt_bigint")
@PrefixGameTestTemplate(false)
public final class BigCandidateIndexGameTests {
    @GameTest(template = "empty", templateNamespace = "ae2lt_global_batch")
    public static void indexKeepsFullMatchingAndFallsBackWithoutRevision(GameTestHelper h) {
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var output = AEItemKey.of(Items.GOLD_INGOT);
        var details = pattern(h, raw, output);
        var recipe = Objects.requireNonNull(BigProviderRecipe.capture(details, List.of(Map.of(raw, 2L)), h.getLevel()));
        var valid = new boolean[]{true};
        var mutable = (IPatternDetails) Proxy.newProxyInstance(IPatternDetails.class.getClassLoader(),
                new Class<?>[]{IPatternDetails.class}, (p, m, a) ->
                        m.getName().equals("getOutputs") && !valid[0]
                                ? new GenericStack[]{new GenericStack(output, 7)} : m.invoke(details, a));
        var wrong = (IPatternDetails) Proxy.newProxyInstance(IPatternDetails.class.getClassLoader(),
                new Class<?>[]{IPatternDetails.class}, (p, m, a) ->
                        m.getName().equals("getOutputs")
                                ? new GenericStack[]{new GenericStack(output, 9)} : m.invoke(details, a));
        var candidates = new ArrayList<IPatternDetails>(List.of(wrong, mutable));
        var queries = new int[1];
        var revision = new long[1];
        for (boolean tracked : new boolean[]{true, false}) {
            queries[0] = 0;
            valid[0] = true;
            candidates.clear();
            candidates.addAll(List.of(wrong, mutable));
            var types = tracked ? new Class<?>[]{ICraftingService.class, CraftingProviderRevision.class}
                    : new Class<?>[]{ICraftingService.class};
            var crafting = (ICraftingService) Proxy.newProxyInstance(ICraftingService.class.getClassLoader(),
                    types, (p, m, a) -> switch (m.getName()) {
                        case "getCraftingFor" -> { queries[0]++; yield List.copyOf(candidates); }
                        case "thunderbolt$getCraftingProviderRevision" -> revision[0];
                        default -> BigServiceTestFixture.defaultValue(m.getReturnType());
                    });
            var lookup = new BigCraftingPatternLookup();
            for (int call = 0; call < 1_000; call++) {
                h.assertTrue(lookup.resolve(recipe, crafting, h.getLevel()) == mutable,
                        "must reject first same-definition candidate and retain source order");
            }
            h.assertTrue(queries[0] == (tracked ? 1 : 1_000), "query bucket scan count " + queries[0]);
            valid[0] = false;
            h.assertTrue(lookup.resolve(recipe, crafting, h.getLevel()) == null,
                    "cached candidates must still run current full matching");
            valid[0] = true;
            candidates.clear();
            revision[0]++;
            h.assertTrue(lookup.resolve(recipe, crafting, h.getLevel()) == null, "removed recipe remained cached");
            candidates.add(details);
            revision[0]++;
            h.assertTrue(lookup.resolve(recipe, crafting, h.getLevel()) == details, "replacement recipe not found");
        }
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ae2lt_global_batch")
    public static void nativeProviderChangesInvalidateWithinTheSameTick(GameTestHelper h) {
        var fixture = new BigServiceTestFixture(h.getLevel());
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var details = pattern(h, raw, AEItemKey.of(Items.GOLD_INGOT));
        var recipe = Objects.requireNonNull(BigProviderRecipe.capture(details, List.of(Map.of(raw, 2L)), h.getLevel()));
        var provider = new ICraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return List.of(details); }
            public boolean isBusy() { return false; }
            public boolean pushPattern(IPatternDetails p, KeyCounter[] inputs) { return false; }
        };
        var lookup = new BigCraftingPatternLookup();
        long tick = h.getLevel().getGameTime();
        h.assertTrue(lookup.resolve(recipe, fixture.crafting, h.getLevel()) == null, "initially absent");
        fixture.addProvider(provider);
        h.assertTrue(lookup.resolve(recipe, fixture.crafting, h.getLevel()) == details, "same-tick addition");
        fixture.removeProvider(provider);
        h.assertTrue(lookup.resolve(recipe, fixture.crafting, h.getLevel()) == null, "same-tick removal");
        fixture.addProvider(provider);
        h.assertTrue(lookup.resolve(recipe, fixture.crafting, h.getLevel()) == details, "same-tick replacement");
        h.assertTrue(h.getLevel().getGameTime() == tick, "test must not advance tick");
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ae2lt_global_batch")
    public static void exactMissingMaterialWaitsForRelevantPhysicalReceipts(GameTestHelper h) {
        var fixture = new BigServiceTestFixture(h.getLevel());
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var output = AEItemKey.of(Items.GOLD_INGOT);
        var details = pattern(h, raw, output);
        var recipe = Objects.requireNonNull(BigProviderRecipe.capture(details, List.of(Map.of(raw, 2L)), h.getLevel()));
        var program = BigExecutionProgram.of(List.of(BigExecutionProgram.block(
                List.of(new BigExecutionProgram.Step<>(recipe.pattern(), BigInteger.ONE)), BigInteger.ONE)));
        var job = new BigCraftingJob(output, BigInteger.valueOf(3), program);
        job.waiting.put(raw, BigInteger.TWO); // Two real inputs are still in flight.
        var calls = new int[1];
        fixture.addProvider(new DeferredCraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return List.of(details); }
            public boolean isBusy() { return false; }
            public boolean pushPattern(IPatternDetails p, KeyCounter[] inputs) { throw new AssertionError("deferred path"); }
            public boolean pushPattern(IPatternDetails p, KeyCounter[] inputs, OutputSink returns) {
                calls[0]++;
                var outputs = new KeyCounter();
                outputs.add(output, 3);
                return returns.enqueue(outputs);
            }
        });
        // Ordinary-provider work must never enumerate matrix machines.
        fixture.gridValues.put("getActiveMachines", new Object());
        fixture.load(job);
        fixture.service.tick();
        h.assertTrue(fixture.service.job().waitingForMaterials(), "unfunded step must park");
        for (int visit = 0; visit < 100; visit++) fixture.service.tick();
        fixture.service.job().retainOutput(AEItemKey.of(Items.EMERALD), BigInteger.ONE);
        h.assertTrue(fixture.service.job().waitingForMaterials(), "unrelated return must not unlock");
        h.assertTrue(fixture.service.insert(raw, 1, Actionable.MODULATE) == 1, "first real receipt");
        fixture.service.tick();
        h.assertTrue(calls[0] == 0 && fixture.service.job().waitingForMaterials(), "partial receipt remains parked");
        h.assertTrue(fixture.service.insert(raw, 1, Actionable.MODULATE) == 1, "second real receipt");
        fixture.service.tick();
        h.assertTrue(calls[0] == 1 && !fixture.service.busy(), "sufficient return unlocks and finishes");
        h.assertTrue(BigInteger.valueOf(3).equals(fixture.disk.contents.get(output)), "exact output conserved");
        h.succeed();
    }

    private static IPatternDetails pattern(GameTestHelper h, AEKey raw, AEKey output) {
        return PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(raw, 2)}, new GenericStack[]{new GenericStack(output, 3)}), h.getLevel());
    }
}
