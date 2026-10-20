package com.moakiee.ae2lt.crafting.big;

import appeng.api.crafting.*;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.crafting.runtime.api.DeferredCraftingProvider;
import com.moakiee.ae2lt.logic.tianshu.CpuInternalCoreCalculator;
import com.moakiee.ae2lt.logic.tianshu.CpuInternalCoreProfile;
import com.moakiee.ae2lt.logic.tianshu.CpuMainCoreTier;
import com.moakiee.thunderbolt.core.crafting.big.BigExecutionProgram;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.*;
import java.math.BigInteger;
import java.util.*;

@GameTestHolder("ae2lt_bigint")
@PrefixGameTestTemplate(false)
public final class BigZeroTickGameTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void sameTickBudgetAndReentrantDeliveryRemainBounded(GameTestHelper h) {
        checkBudget(h, CpuInternalCoreCalculator.calculate(CpuMainCoreTier.MULTIDIMENSIONAL, 0, 0));
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void workBudgetUsesHostProfileInsteadOfAFixedLimit(GameTestHelper h) {
        // Deterministic lower quota while retaining the exact-storage fixture's availability.
        checkBudget(h, new CpuInternalCoreProfile(null, 0, 0, 0,
                Long.MAX_VALUE, 256, Long.MAX_VALUE, true, false));
    }

    private static void checkBudget(GameTestHelper h, CpuInternalCoreProfile profile) {
        int budget = profile.successfulDispatchesPerTick();
        int copies = budget + 1;
        var fixture = new BigServiceTestFixture(h.getLevel(), profile);
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var output = AEItemKey.of(Items.GOLD_INGOT);
        var details = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(raw, 2)), List.of(new GenericStack(output, 3))), h.getLevel());
        var recipe = Objects.requireNonNull(BigProviderRecipe.capture(details, List.of(Map.of(raw, 2L)), h.getLevel()));
        var program = BigExecutionProgram.of(List.of(BigExecutionProgram.block(
                List.of(new BigExecutionProgram.Step<>(recipe.pattern(), BigInteger.valueOf(copies))), BigInteger.ONE)));
        var job = new BigCraftingJob(output, BigInteger.valueOf(3L * copies), program);
        job.escrow.putAll(program.required());
        var calls = new int[1];
        var provider = new DeferredCraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return List.of(details); }
            public boolean isBusy() { return false; }
            public boolean pushPattern(IPatternDetails p, KeyCounter[] input) { throw new AssertionError("delayed path"); }
            public boolean pushPattern(IPatternDetails p, KeyCounter[] input, OutputSink returns) {
                calls[0]++;
                fixture.service.tick(); // Provider-driven recursion must not dispatch anything.
                var outputs = new KeyCounter(); outputs.add(output, 3);
                h.assertTrue(returns.enqueue(outputs), "physical receipt rejected");
                return true;
            }
        };
        fixture.crafting.addGlobalCraftingProvider(provider);
        fixture.load(job);
        fixture.service.tick();
        h.assertTrue(calls[0] == budget, "tick budget lost: " + calls[0]);
        h.assertTrue(fixture.service.job().waiting.isEmpty(), "last-budget return was not drained");
        h.assertTrue(fixture.service.job().escrow.get(output).equals(BigInteger.valueOf(3L * budget)), "last return missing");
        fixture.service.tick();
        h.assertTrue(calls[0] == budget, "second invocation reset the same-tick budget");
        fixture.service.load(fixture.service.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        fixture.disk.beforeInsert = fixture.service::tick;
        h.runAfterDelay(1, () -> {
            fixture.service.tick();
            h.assertTrue(calls[0] == copies && !fixture.service.busy(), "final dispatch not completed");
            h.assertTrue(fixture.disk.contents.get(output).equals(BigInteger.valueOf(3L * copies)),
                    "reentrant storage callback duplicated delivery");
            h.succeed();
        });
    }
}
