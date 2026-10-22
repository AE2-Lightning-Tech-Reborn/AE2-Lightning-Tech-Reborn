package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.CraftingPlan;
import appeng.me.service.CraftingService;
import com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.MatrixPortBlockEntity;
import com.moakiee.ae2lt.crafting.matrix.core.CraftingCoreHost;
import com.moakiee.ae2lt.crafting.matrix.core.CraftingCoreRegistry;
import com.moakiee.ae2lt.crafting.matrix.core.MolecularCopyAssembler;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuHost;
import com.moakiee.ae2lt.crafting.runtime.ExecuteLoopPattern;
import com.moakiee.thunderbolt.core.crafting.batch.BatchCopyLimitPattern;
import com.moakiee.thunderbolt.core.crafting.batch.SharedBatchInputPattern;
import com.moakiee.thunderbolt.core.crafting.loop.ClosedLoopBatchPatternDetails;
import com.moakiee.thunderbolt.core.crafting.loop.CraftingTaskPersistenceDefinition;
import com.moakiee.thunderbolt.core.crafting.loop.ISeedPreservingCraftingTask;
import com.moakiee.ae2lt.logic.craft.MatrixCraftCore;
import com.moakiee.ae2lt.logic.craft.MatrixCraftingCluster;
import com.moakiee.ae2lt.logic.craft.MatrixCraftingEnergy;
import com.moakiee.ae2lt.logic.craft.MatrixCraftingUnit;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real AE2 recipes, transformed dispatch code, matrix port/controller/core and CPU accounting. */
@GameTestHolder("ae2lt_deferred")
@PrefixGameTestTemplate(false)
public final class TianshuDeferredReturnsGameTests {
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void nativeSubstitutionWaitsForAlternativeMaterialsWithoutPeriodicAttempts(GameTestHelper h) throws Exception {
        var details = pattern(h.getLevel(), "stick", new ItemStack(Items.STICK, 4),
                Map.of(0, new ItemStack(Items.OAK_PLANKS), 3, new ItemStack(Items.OAK_PLANKS)), true);
        h.assertTrue(details.getInputs()[0].getPossibleInputs().length > 1, "real alternative ingredient list");
        var fixture = new Fixture(h.getLevel(), true, List.of(details));
        var oak = AEItemKey.of(Items.OAK_PLANKS);
        var birch = AEItemKey.of(Items.BIRCH_PLANKS);
        submitMaterialTest(fixture, details, Map.of(oak, 2L));
        var inventory = fixture.cpu.getCraftingLogic().getInventory();
        inventory.extract(oak, 2, Actionable.MODULATE);
        fixture.run(32);
        int first = fixture.providerQueries;
        for (int tick = 1; tick <= 40; tick++) h.runAfterDelay(tick, () -> fixture.run(32));
        h.runAfterDelay(41, () -> {
            h.assertTrue(fixture.providerQueries == first, "substitution must not keep probing providers");
            inventory.insert(AEItemKey.of(Items.EMERALD), 1, Actionable.MODULATE);
            fixture.run(32);
            inventory.insert(birch, 1, Actionable.MODULATE);
            fixture.run(32);
            h.assertTrue(fixture.providerQueries == first, "unrelated or insufficient alternative return stays parked");
            String partialState = materialWaitState(fixture, details);
            inventory.insert(oak, 1, Actionable.MODULATE);
            String readyState = materialWaitState(fixture, details);

            long oakBefore = inventory.list.get(oak);
            long birchBefore = inventory.list.get(birch);
            int dispatched = fixture.run(32);
            String afterState = materialWaitState(fixture, details);
            com.mojang.logging.LogUtils.getLogger().info("GTO_MATERIAL_WAIT partial={} ready={} after={}", partialState, readyState, afterState);
            h.assertTrue(dispatched == 1, "two alternative plank types must unlock one native craft"
                    + "; dispatched=" + dispatched + "; before=" + oakBefore + "/" + birchBefore
                    + "; after=" + inventory.list.get(oak) + "/" + inventory.list.get(birch)
                    + "; queries=" + fixture.providerQueries + "; busy=" + fixture.cpu.isBusy()
                    + "; partial=" + partialState + "; ready=" + readyState + "; after=" + afterState);
            h.assertTrue(fixture.disk.items.get(AEItemKey.of(Items.STICK)) == 4 && !fixture.cpu.isBusy(),
                    "native mixed-alternative recipe completed");
            h.succeed();
        });
    }

    private static String materialWaitState(Fixture fixture, IPatternDetails details) {
        try {
            Object logic = fixture.cpu.getCraftingLogic();
            var readiness = logic.getClass().getDeclaredField("materialReadiness");
            readiness.setAccessible(true);
            Object probe = readiness.get(logic);
            var missing = probe.getClass().getDeclaredMethod("missing", IPatternDetails.class);
            missing.setAccessible(true);
            var queued = logic.getClass().getDeclaredField("queuedTasks");
            queued.setAccessible(true);
            var state = new java.util.LinkedHashMap<String, Object>();
            state.put("missing", ((Set<?>) missing.invoke(probe, details)).size());
            state.put("queued", ((Set<?>) queued.get(logic)).size());
            state.put("now", appeng.hooks.ticking.TickHandler.instance().getCurrentTick());
            for (var name : List.of("wheelCursor", "schedulerTick", "batchTick", "execution")) {
                var field = logic.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(logic);
                state.put(name, name.equals("execution")
                        ? ((com.moakiee.ae2lt.crafting.runtime.CraftingExecutionState) value).busy() : value);
            }
            var wheel = logic.getClass().getDeclaredField("taskWheel");
            wheel.setAccessible(true);
            var buckets = (java.util.ArrayDeque<?>[]) wheel.get(logic);
            for (int i = 0; i < buckets.length; i++) if (!buckets[i].isEmpty()) state.put("bucket" + i, buckets[i].size());
            return state.toString();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void idOnlyOverloadWakesForANewComponentVariant(GameTestHelper h) throws Exception {
        var captured = new ItemStack(Items.IRON_INGOT, 2);
        captured.setHoverName( Component.literal("encoded"));
        var variant = new ItemStack(Items.IRON_INGOT);
        variant.setHoverName( Component.literal("returned"));
        var sourceKey = AEItemKey.of(captured);
        var actual = AEItemKey.of(variant);
        var output = AEItemKey.of(Items.DIAMOND);
        var source = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(sourceKey, 2)}, new GenericStack[]{new GenericStack(output, 1)}), h.getLevel());
        var parsed = new com.moakiee.ae2lt.overload.runtime.pattern.ParsedPatternDefinition(
                new com.moakiee.ae2lt.overload.runtime.pattern.SourcePatternSnapshot(
                        new ResourceLocation("ae2lt", "material_wake_test"), null, null),
                List.of(new com.moakiee.ae2lt.overload.runtime.pattern.ParsedPatternInput(0, captured)),
                List.of(new com.moakiee.ae2lt.overload.runtime.pattern.ParsedPatternOutput(
                        0, new ItemStack(Items.DIAMOND), true)));
        var metadata = com.moakiee.ae2lt.overload.runtime.model.EncodedOverloadPattern.builder()
                .input(0, com.moakiee.ae2lt.overload.runtime.model.MatchMode.ID_ONLY).build();
        var details = new com.moakiee.ae2lt.overload.runtime.pattern.Ae2OverloadPatternDetails(
                AEItemKey.of(Items.PAPER),
                new com.moakiee.ae2lt.overload.runtime.pattern.OverloadPatternDetails(parsed, metadata), source);
        var fixture = new Fixture(h.getLevel(), true, List.of(details));
        fixture.externalProvider = new ICraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return List.of(details); }
            public boolean isBusy() { return false; }
            public boolean pushPattern(IPatternDetails p, KeyCounter[] inputs) {
                h.assertTrue(inputs[0].get(actual) == 2, "actual returned components must reach provider unchanged");
                return true;
            }
        };
        submitMaterialTest(fixture, details, Map.of(sourceKey, 2L));
        var inventory = fixture.cpu.getCraftingLogic().getInventory();
        inventory.extract(sourceKey, 2, Actionable.MODULATE);
        fixture.run(32);
        int first = fixture.providerQueries;
        for (int tick = 1; tick <= 40; tick++) h.runAfterDelay(tick, () -> fixture.run(32));
        h.runAfterDelay(41, () -> {
            h.assertTrue(fixture.providerQueries == first, "ID_ONLY missing input must not keep probing");
            inventory.insert(actual, 1, Actionable.MODULATE);
            fixture.run(32);
            h.assertTrue(fixture.providerQueries == first, "partial same-id return must remain parked");
            inventory.insert(actual, 1, Actionable.MODULATE);
            h.assertTrue(fixture.run(32) == 1, "new component variant must unlock ID_ONLY input");
            h.assertTrue(fixture.network.insert(output, 1, Actionable.MODULATE, IActionSource.empty()) == 1,
                    "physical output accepted");
            fixture.run(32);
            h.assertTrue(!fixture.cpu.isBusy(), "ID_ONLY task completed");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void unknownCandidateChangesRefreshWithoutRepeatedExtraction(GameTestHelper h) throws Exception {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var output = AEItemKey.of(Items.DIAMOND);
        var choices = new GenericStack[][]{{new GenericStack(iron, 1)}};
        var matches = new int[1];
        var worldAllows = new boolean[]{false};
        var details = new IPatternDetails() {
            public IInput[] getInputs() { return new IInput[]{new IInput() {
                public GenericStack[] getPossibleInputs() { return choices[0]; }
                public long getMultiplier() { return 1; }
                public boolean isValid(AEKey key, Level level) {
                    matches[0]++;
                    return worldAllows[0]
                            && Arrays.stream(choices[0]).anyMatch(option -> option.what().equals(key));
                }
                public AEKey getRemainingKey(AEKey key) { return null; }
            }}; }
            public GenericStack[] getOutputs() { return new GenericStack[]{new GenericStack(output, 1)}; }
            public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
        };
        var fixture = new Fixture(h.getLevel(), true, List.of(details));
        var pushes = new int[1];
        fixture.externalProvider = new ICraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return List.of(details); }
            public boolean isBusy() { return false; }
            public boolean pushPattern(IPatternDetails p, KeyCounter[] inputs) { pushes[0]++; return true; }
        };
        // No iron has arrived yet; gold is already held when the candidate definition later changes.
        submitMaterialTest(fixture, details, Map.of(gold, 1L));
        fixture.providerQueries = 0;
        matches[0] = 0;
        for (int tick = 1; tick <= 73; tick++) h.runAfterDelay(tick, () -> fixture.run(32));
        h.runAfterDelay(40, () -> {
            h.assertTrue(matches[0] == 0 && fixture.providerQueries == 0,
                    "safety refresh must not call native matching, extraction or providers");
            choices[0] = new GenericStack[]{new GenericStack(gold, 1)};
        });
        h.runAfterDelay(66, () -> {
            h.assertTrue(matches[0] > 0 && pushes[0] == 0,
                    "funded candidate must still obey dynamic native validity");
            worldAllows[0] = true;
        });
        h.runAfterDelay(74, () -> {
            h.assertTrue(pushes[0] == 1,
                    "candidate and world-state changes must unlock already-held material without a return");
            h.assertTrue(fixture.network.insert(output, 1, Actionable.MODULATE, IActionSource.empty()) == 1,
                    "physical dynamic output accepted");
            fixture.run(32);
            h.assertTrue(!fixture.cpu.isBusy(), "dynamic task completed");
            h.succeed();
        });
    }

    private static void submitMaterialTest(Fixture fixture, IPatternDetails details, Map<AEKey, Long> stock) {
        var used = new KeyCounter();
        stock.forEach(used::add);
        fixture.disk.items.addAll(used);
        var plan = new CraftingPlan(details.getPrimaryOutput(), 100,
                false, false, used, new KeyCounter(), new KeyCounter(), new it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<>(Map.of(details, 1L)));
        if (!fixture.cpu.getCraftingLogic().trySubmitJob(
                fixture.host.getGrid(), plan, IActionSource.empty(), null).successful()) {
            throw new AssertionError("material test submission failed");
        }
    }

    @GameTest(template = "empty")
    public static void asynchronousReceiptListenerCannotDispatchBeforeReceiptCompletes(GameTestHelper helper) throws Exception {
        var fixture = new Fixture(helper.getLevel(), true);
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var intermediate = AEItemKey.of(Items.GOLD_INGOT);
        var output = AEItemKey.of(Items.DIAMOND);
        var first = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(raw, 1)}, new GenericStack[]{new GenericStack(intermediate, 1)}), helper.getLevel());
        var second = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(intermediate, 1)}, new GenericStack[]{new GenericStack(output, 1)}), helper.getLevel());
        fixture.externalProvider = new ICraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return List.of(first, second); }
            public boolean isBusy() { return false; }
            public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) { return true; }
        };
        var used = new KeyCounter();
        used.add(raw, 1);
        fixture.disk.items.add(raw, 1);
        var plan = new CraftingPlan(new GenericStack(output, 1), 100,
                false, false, used, new KeyCounter(), new KeyCounter(), new it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<>(Map.of(first, 1L, second, 1L)));
        helper.assertTrue(fixture.cpu.getCraftingLogic().trySubmitJob(
                fixture.host.getGrid(), plan, IActionSource.empty(), null).successful(), "submitted chain");
        helper.assertTrue(fixture.run(32) == 1, "upstream dispatched before physical return");
        var callbacks = new int[1];
        java.util.function.Consumer<AEKey> listener = key -> {
            callbacks[0]++;
            helper.assertTrue(fixture.run(32) == 0, "receipt callback must not recursively dispatch downstream");
        };
        fixture.cpu.getCraftingLogic().addListener(listener);
        helper.assertTrue(fixture.cpu.getCraftingLogic().insert(intermediate, 1, Actionable.MODULATE) == 1,
                "physical intermediate accepted");
        fixture.cpu.getCraftingLogic().removeListener(listener);
        helper.assertTrue(callbacks[0] > 0, "receipt must actually invoke listeners");
        helper.assertTrue(fixture.run(32) == 1, "downstream runs after receipt completes");
        helper.assertTrue(fixture.network.insert(output, 1, Actionable.MODULATE, IActionSource.empty()) == 1,
                "physical final output accepted");
        fixture.run(32);
        helper.assertTrue(!fixture.cpu.isBusy() && fixture.disk.items.get(output) == 1, "one conserved output");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void missingMaterialWaitsForRelevantSufficientInventoryChange(GameTestHelper helper) throws Exception {
        var fixture = new Fixture(helper.getLevel(), true);
        fixture.submitProcessingJob(helper.getLevel());
        var inventory = fixture.cpu.getCraftingLogic().getInventory();
        var raw = AEItemKey.of(Items.OAK_LOG);
        helper.assertTrue(inventory.extract(raw, 2, Actionable.MODULATE) == 2, "remove funded material");
        fixture.providerQueries = 0;
        helper.assertTrue(fixture.run(32) == 0, "missing material cannot dispatch");
        int firstAttempt = fixture.providerQueries;
        for (int tick = 1; tick <= 40; tick++) {
            helper.runAfterDelay(tick, () -> fixture.run(32));
        }
        helper.runAfterDelay(41, () -> {
            helper.assertTrue(fixture.providerQueries == firstAttempt,
                    "no periodic provider/extraction probes after initial attempt: " + fixture.providerQueries);
            inventory.insert(AEItemKey.of(Items.EMERALD), 1, Actionable.MODULATE);
            fixture.run(32);
            helper.assertTrue(fixture.providerQueries == firstAttempt, "unrelated inventory change must not wake");
            inventory.insert(raw, 1, Actionable.MODULATE);
            fixture.run(32);
            helper.assertTrue(fixture.providerQueries == firstAttempt, "partial receipt must remain parked");
            // The inventory hook also covers input refunds; no explicit machine-return wake call.
            inventory.insert(raw, 1, Actionable.MODULATE);
            helper.assertTrue(fixture.run(32) == 1, "sufficient refund wakes immediately");
            fixture.deliverProcessingOutputs();
            fixture.run(32);
            helper.assertTrue(!fixture.cpu.isBusy(), "woken task completes");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void busyPowerAndRejectionRetryWithoutMaterialReturns(GameTestHelper helper) throws Exception {
        var fixture = new Fixture(helper.getLevel(), true);
        fixture.submitProcessingJob(helper.getLevel());
        fixture.providerBusy = true;
        helper.assertTrue(fixture.run(32) == 0, "busy provider waits");
        helper.runAfterDelay(1, () -> {
            fixture.providerBusy = false;
            fixture.energyAvailable = false;
            helper.assertTrue(fixture.run(32) == 0, "unpowered provider waits");
        });
        helper.runAfterDelay(5, () -> {
            fixture.energyAvailable = true;
            fixture.providerRejects = true;
            helper.assertTrue(fixture.run(32) == 0, "rejected input must be refunded");
        });
        helper.runAfterDelay(10, () -> {
            fixture.providerRejects = false;
            helper.assertTrue(fixture.run(32) == 1, "cooldown must retry without any machine return");
            fixture.deliverProcessingOutputs();
            fixture.run(32);
            helper.assertTrue(!fixture.cpu.isBusy(), "retry task completes");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void globalAdapterRunsThroughActualTimeWheelCpu(GameTestHelper helper) throws Exception {
        var fixture = new Fixture(helper.getLevel(), true);
        var nativeProvider = fixture.service.getProviders(fixture.patterns.get(0)).iterator().next();
        fixture.providers.remove(nativeProvider);
        var ordinary = new ICraftingProvider() {
            @Override public List<IPatternDetails> getAvailablePatterns() { return nativeProvider.getAvailablePatterns(); }
            @Override public boolean isBusy() { return nativeProvider.isBusy(); }
            @Override public boolean pushPattern(IPatternDetails pattern, KeyCounter[] input) {
                throw new AssertionError("time-wheel CPU must use the global batch adapter");
            }
        };
        var id = new ResourceLocation("ae2lt_global_batch", "timewheel");
        com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapters.register(id,
                (provider, pattern, job) -> provider == ordinary
                        ? (com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider) nativeProvider : null);
        fixture.providers.add(ordinary);
        try {
            fixture.submitChain();
            helper.assertTrue(fixture.run(32) == 3, "three batch calls through the global adapter");
            fixture.assertFinished(helper);
        } finally {
            com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapters.unregister(id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedClosedLoopSeedReturnsBetweenBatchesInSameTick(GameTestHelper helper) throws Exception {
        runSeedTest(helper, false, 2);
    }

    @GameTest(template = "empty")
    public static void concreteSeedSinglePushReturnsAfterItsLedgerCommit(GameTestHelper helper) throws Exception {
        runSeedTest(helper, true, 4);
    }

    private static void runSeedTest(GameTestHelper helper, boolean alternatives, int expectedDispatches) throws Exception {
        var recipe = new CatalystPattern(alternatives);
        var fixture = new Fixture(helper.getLevel(), true, List.of(recipe));
        var seed = AEItemKey.of(Items.EMERALD);
        var consumer = UUID.randomUUID();
        var seedAmount = new KeyCounter();
        seedAmount.add(seed, 1);
        var task = new ExecuteLoopPattern(recipe, consumer, seedAmount, seedAmount, Map.of(consumer, seedAmount));
        var used = new KeyCounter();
        used.add(seed, 1);
        used.add(AEItemKey.of(Items.REDSTONE), 4);
        fixture.disk.items.add(seed, 1);
        fixture.disk.items.add(AEItemKey.of(Items.REDSTONE), 4);
        var plan = new CraftingPlan(new GenericStack(AEItemKey.of(Items.DIAMOND), 4), 100,
                false, false, used, new KeyCounter(), new KeyCounter(), new it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<>(Map.of(task, 4L)));
        var result = fixture.cpu.getCraftingLogic().trySubmitJob(fixture.host.getGrid(), plan, IActionSource.empty(), null);
        helper.assertTrue(result.successful(), "seed task submitted");
        helper.assertTrue(fixture.run(32) == expectedDispatches, "seed must return before next dispatch");
        helper.assertTrue(!fixture.cpu.getCraftingLogic().hasJob(), "seed task completed");
        helper.assertTrue(fixture.disk.items.get(AEItemKey.of(Items.DIAMOND)) == 4, "four produced outputs");
        helper.assertTrue(fixture.returnedSeeds.get(seed) == 1, "exactly one private seed returned");
        helper.assertTrue(fixture.disk.items.get(seed) == 0, "seed remains private");
        helper.assertTrue(!fixture.cpu.isBusy(), "no seed or return remains stranded");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void storageCallbackCannotReenterDispatchOrDrain(GameTestHelper helper) throws Exception {
        var fixture = new Fixture(helper.getLevel(), true);
        fixture.submitChain();
        fixture.disk.beforeInsert = () -> helper.assertTrue(fixture.run(32) == 0, "recursive dispatch rejected");
        fixture.run(32);
        fixture.assertFinished(helper);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void threeLayerBatchChainCompletesWithoutAdvancingTick(GameTestHelper helper) throws Exception {
        var fixture = new Fixture(helper.getLevel(), true);
        fixture.submitChain();
        long tick = helper.getLevel().getGameTime();
        int operations = fixture.run(32);
        helper.assertTrue(operations == 3, "one batch dispatch per layer: " + operations);
        fixture.assertFinished(helper);
        helper.assertTrue(helper.getLevel().getGameTime() == tick, "execution must stay in one tick");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryPushPathAlsoCompletesInOneTick(GameTestHelper helper) throws Exception {
        var fixture = new Fixture(helper.getLevel(), false);
        fixture.submitChain();
        helper.assertTrue(fixture.run(32) == 8, "ordinary pushes charge all eight copies");
        fixture.assertFinished(helper);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lastDispatchBudgetStillDrainsItsReturn(GameTestHelper helper) throws Exception {
        var fixture = new Fixture(helper.getLevel(), true);
        fixture.submitChain();
        helper.assertTrue(fixture.run(1) == 1, "exactly one dispatch");
        helper.assertTrue(fixture.cpu.getCraftingLogic().getInventory().list.get(AEItemKey.of(Items.OAK_PLANKS)) == 8,
                "last-budget return already available inside CPU");
        helper.assertTrue(fixture.disk.items.get(AEItemKey.of(Items.OAK_PLANKS)) == 0,
                "intermediate output does not pass through network storage");
        helper.assertTrue(fixture.cluster.threadsInFlight() == 0, "matrix has no five-tick pending output");
        helper.runAfterDelay(1, () -> {
            fixture.run(32);
            fixture.assertFinished(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void blockedFinalOutputSurvivesSaveAndReload(GameTestHelper helper) throws Exception {
        var fixture = new Fixture(helper.getLevel(), true);
        fixture.submitChain();
        fixture.disk.blocked = true;
        fixture.run(32);
        helper.assertTrue(!fixture.cpu.getCraftingLogic().hasJob(), "physical production completed");
        helper.assertTrue(fixture.cpu.isBusy(), "undelivered physical items keep the CPU alive");
        var data = new CompoundTag();
        fixture.cpu.getCraftingLogic().writeToNBT(data, helper.getLevel().registryAccess());
        helper.assertTrue(data.contains("deferredDeliveryRemainders"), "blocked direct outputs persisted");
        var restored = new TimeWheelCraftingCPU(fixture.host, Long.MAX_VALUE, 31, Long.MAX_VALUE, false);
        restored.getCraftingLogic().readFromNBT(data, helper.getLevel().registryAccess());
        fixture.disk.blocked = false;
        restored.getCraftingLogic().tickCraftingLogic(fixture.energy, fixture.service);
        helper.assertTrue(fixture.disk.items.get(AEItemKey.of(Items.LADDER)) == 6, "all six ladders delivered once");
        helper.assertTrue(fixture.disk.items.get(AEItemKey.of(Items.STICK)) == 2, "surplus sticks preserved");
        helper.assertTrue(!restored.isBusy(), "restored CPU drains fully");
        helper.succeed();
    }

    private static final class Fixture {
        final Disk disk = new Disk();
        final appeng.me.storage.NetworkStorage network = new appeng.me.storage.NetworkStorage();
        ICraftingProvider externalProvider;
        final KeyCounter processingOutputs = new KeyCounter();
        int providerQueries;
        boolean providerBusy, providerRejects;
        boolean energyAvailable = true;
        final KeyCounter returnedSeeds = new KeyCounter();
        final IEnergyService energy;
        final CraftingService service;
        final TimeWheelCraftingCpuHost host;
        final TimeWheelCraftingCPU cpu;
        final List<ICraftingProvider> providers = new java.util.ArrayList<>();
        final MatrixCraftingCluster cluster;
        final List<IPatternDetails> patterns;

        Fixture(ServerLevel level, boolean batch) throws Exception {
            this(level, batch, null);
        }

        Fixture(ServerLevel level, boolean batch, List<IPatternDetails> customPatterns) throws Exception {
            patterns = customPatterns != null ? customPatterns : List.of(
                    pattern(level, "oak_planks", new ItemStack(Items.OAK_PLANKS, 4),
                            Map.of(0, new ItemStack(Items.OAK_LOG))),
                    pattern(level, "stick", new ItemStack(Items.STICK, 4),
                            Map.of(0, new ItemStack(Items.OAK_PLANKS), 3, new ItemStack(Items.OAK_PLANKS))),
                    pattern(level, "ladder", new ItemStack(Items.LADDER, 3),
                            Map.of(0, new ItemStack(Items.STICK), 2, new ItemStack(Items.STICK),
                                    3, new ItemStack(Items.STICK), 4, new ItemStack(Items.STICK),
                                    5, new ItemStack(Items.STICK), 6, new ItemStack(Items.STICK),
                                    8, new ItemStack(Items.STICK))));
            energy = (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(),
                    new Class<?>[]{IEnergyService.class}, (p, method, args) ->
                            method.getName().equals("extractAEPower")
                                    ? (energyAvailable ? args[0] : 0D) : defaultValue(method.getReturnType()));
            network.mount(0, disk);
            var storage = proxy(IStorageService.class, Map.of("getInventory", network, "getCachedInventory", disk.items));
            var gridValues = new HashMap<String, Object>();
            gridValues.put("getStorageService", storage);
            gridValues.put("getEnergyService", energy);
            var grid = proxy(IGrid.class, gridValues);
            var matrixHost = new CraftingCoreHost() {
                @Override public long getGameTime() { return level.getGameTime(); }
                @Override public boolean isRemoved() { return false; }
                @Override public boolean isConnected() { return true; }
                @Override public long insertToNetwork(AEKey key, long amount) {
                    throw new AssertionError("direct matrix chain must not use delayed network delivery");
                }
                @Override public void spawnToWorld(AEKey key, long amount) { throw new AssertionError("lost output"); }
            };
            var units = new MatrixCraftCore() {
                @Override public List<MatrixCraftingUnit> craftingUnits() {
                    return List.of(MatrixCraftingUnit.stableCore(), MatrixCraftingUnit.t2Threader());
                }
            };
            cluster = new MatrixCraftingCluster(() -> true, List.of(() -> patterns), List.of(units),
                    matrixHost, new MolecularCopyAssembler(level), new CraftingCoreRegistry(), MatrixCraftingEnergy.UNLIMITED);
            var controller = new MatrixControllerBlockEntity(BlockPos.ZERO,
                    ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER.get().defaultBlockState());
            var clusterField = MatrixControllerBlockEntity.class.getDeclaredField("cluster");
            clusterField.setAccessible(true);
            clusterField.set(controller, cluster);
            // Only replace world/structure discovery; dispatch uses the production port and controller.
            var port = new MatrixPortBlockEntity(BlockPos.ZERO,
                    ModBlocks.MATTER_WARPING_MATRIX_PORT.get().defaultBlockState()) {
                @Override public MatrixControllerBlockEntity getController() { return controller; }
                @Override public boolean isFormed() { return true; }
                @Override public long getBatchCapacity(IPatternDetails details) {
                    return batch ? super.getBatchCapacity(details) : 0L;
                }
            };
            providers.add(port);
            service = new CraftingService(grid, storage, energy) {
                @Override public Iterable<ICraftingProvider> getProviders(IPatternDetails details) { providerQueries++; return externalProvider == null ? providers : List.of(externalProvider); }
            };
            gridValues.put("getCraftingService", service);
            host = new TimeWheelCraftingCpuHost() {
                @Override public boolean isCpuActive() { return true; }
                @Override public IGrid getGrid() { return grid; }
                @Override public IActionSource getActionSource() { return IActionSource.empty(); }
                @Override public Level getCpuLevel() { return level; }
                @Override public void markCpuDirty() { }
                @Override public Component getCpuDisplayName() { return Component.literal("Deferred test CPU"); }
                @Override public long insertReusableSeed(AEKey key, long amount, Actionable mode) {
                    if (mode == Actionable.MODULATE) returnedSeeds.add(key, amount);
                    return amount;
                }
            };
            cpu = new TimeWheelCraftingCPU(host, Long.MAX_VALUE, 31, Long.MAX_VALUE, false);
            network.mount(Integer.MAX_VALUE, new MEStorage() {
                public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                    return cpu.getCraftingLogic().insert(key, amount, mode);
                }
                public Component getDescription() { return Component.literal("Test CPU return routing"); }
            });
        }

        void submitProcessingJob(ServerLevel level) {
            var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                    new GenericStack[]{new GenericStack(AEItemKey.of(Items.OAK_LOG), 2)},
                    new GenericStack[]{new GenericStack(AEItemKey.of(Items.LADDER), 6),
                            new GenericStack(AEItemKey.of(Items.STICK), 2)}), level);
            externalProvider = new ICraftingProvider() {
                @Override public List<IPatternDetails> getAvailablePatterns() { return List.of(pattern); }
                @Override public boolean isBusy() { return providerBusy; }
                @Override public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
                    if (providerRejects) return false;
                    if (inputs[0].get(AEItemKey.of(Items.OAK_LOG)) != 2) {
                        throw new AssertionError("processing recipe must receive both logs");
                    }
                    for (var output : details.getOutputs()) processingOutputs.add(output.what(), output.amount());
                    return true;
                }
            };
            var used = new KeyCounter();
            used.add(AEItemKey.of(Items.OAK_LOG), 2);
            disk.items.add(AEItemKey.of(Items.OAK_LOG), 2);
            var plan = new CraftingPlan(new GenericStack(AEItemKey.of(Items.LADDER), 6), 100,
                    false, false, used, new KeyCounter(), new KeyCounter(), new it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<>(Map.of(pattern, 1L)));
            var result = cpu.getCraftingLogic().trySubmitJob(host.getGrid(), plan, IActionSource.empty(), null);
            if (!result.successful()) throw new AssertionError("processing submission failed: " + result);
        }

        void deliverProcessingOutputs() {
            for (var output : processingOutputs) {
                long accepted = network.insert(output.getKey(), output.getLongValue(), Actionable.MODULATE, IActionSource.empty());
                if (accepted != output.getLongValue()) throw new AssertionError("processing output left in machine");
            }
            processingOutputs.clear();
        }

        void submitChain() {
            var used = new KeyCounter();
            used.add(AEItemKey.of(Items.OAK_LOG), 2);
            disk.items.add(AEItemKey.of(Items.OAK_LOG), 2);
            var plan = new CraftingPlan(new GenericStack(AEItemKey.of(Items.LADDER), 6), 100,
                    false, false, used, new KeyCounter(), new KeyCounter(),
                    new it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<>(Map.of(patterns.get(0), 2L, patterns.get(1), 4L, patterns.get(2), 2L)));
            var result = cpu.getCraftingLogic().trySubmitJob(host.getGrid(), plan, IActionSource.empty(), null);
            if (!result.successful()) throw new AssertionError("submission failed: " + result);
        }

        int run(int budget) {
            return cpu.getCraftingLogic().tickCraftingLogic(energy, service, budget, Long.MAX_VALUE)
                    .successfulDispatches();
        }

        void assertFinished(GameTestHelper helper) {
            helper.assertTrue(!cpu.getCraftingLogic().hasJob(), "chain completed");
            helper.assertTrue(disk.items.get(AEItemKey.of(Items.LADDER)) == 6, "six real crafted ladders");
            helper.assertTrue(disk.items.get(AEItemKey.of(Items.STICK)) == 2, "two surplus sticks");
            helper.assertTrue(cluster.threadsInFlight() == 0, "no matrix outputs left buffered");
            helper.assertTrue(!cpu.isBusy(), "no CPU state stranded");
        }
    }

    /** Deterministic test recipe with one reusable catalyst, also exercising actual-key seed dispatch. */
    private static final class CatalystPattern implements IMolecularAssemblerSupportedPattern,
            SharedBatchInputPattern, BatchCopyLimitPattern, ISeedPreservingCraftingTask,
            CraftingTaskPersistenceDefinition, ClosedLoopBatchPatternDetails {
        private final UUID group = UUID.randomUUID();
        private final boolean alternatives;
        private final AEItemKey seed = AEItemKey.of(Items.EMERALD);
        CatalystPattern(boolean alternatives) { this.alternatives = alternatives; }
        @Override public IInput[] getInputs() {
            return new IInput[]{input(seed, true), input(AEItemKey.of(Items.REDSTONE), false)};
        }
        private IInput input(AEItemKey key, boolean catalyst) {
            return new IInput() {
                @Override public GenericStack[] getPossibleInputs() {
                    return catalyst && alternatives
                            ? new GenericStack[]{new GenericStack(key, 1), new GenericStack(AEItemKey.of(Items.AMETHYST_SHARD), 1)}
                            : new GenericStack[]{new GenericStack(key, 1)};
                }
                @Override public long getMultiplier() { return 1; }
                @Override public boolean isValid(AEKey actual, Level level) {
                    return Arrays.stream(getPossibleInputs()).anyMatch(candidate -> candidate.what().equals(actual));
                }
                @Override public AEKey getRemainingKey(AEKey actual) { return catalyst ? actual : null; }
            };
        }
        @Override public GenericStack[] getOutputs() { return new GenericStack[] {new GenericStack(AEItemKey.of(Items.DIAMOND), 1)}; }
        @Override public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
        @Override public void fillCraftingGrid(KeyCounter[] inputs, CraftingGridAccessor grid) {
            for (int slot = 0; slot < inputs.length; slot++) {
                for (var entry : inputs[slot]) {
                    if (entry.getLongValue() > 0) {
                        grid.set(slot, ((AEItemKey) entry.getKey()).toStack());
                        entry.setValue(entry.getLongValue() - 1);
                        break;
                    }
                }
            }
        }
        @Override public ItemStack assemble(net.minecraft.world.Container input, Level level) { return new ItemStack(Items.DIAMOND); }
        @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) {
            var remaining = NonNullList.withSize(input.getContainerSize(), ItemStack.EMPTY);
            remaining.set(0, input.getItem(0).copy());
            return remaining;
        }
        @Override public boolean isItemValid(int slot, AEItemKey key, Level level) { return true; }
        @Override public boolean isSlotEnabled(int slot) { return slot < 2; }
        @Override public boolean isSharedBatchInput(int slot, AEKey key) { return slot == 0; }
        @Override public long maxBatchCopies() { return 2; }
        @Override public UUID reusableSeedGroupId() { return group; }
        @Override public Set<AEKey> reusableSeedCycleKeys() { return Set.of(seed); }
        @Override public boolean hasSingleSeedInputPerMember() { return true; }
        @Override public AEItemKey craftingTaskPersistenceDefinition() { return getDefinition(); }
    }

    private static IPatternDetails pattern(ServerLevel level, String id, ItemStack output,
                                            Map<Integer, ItemStack> slots) {
        return pattern(level, id, output, slots, false);
    }

    static IPatternDetails pattern(ServerLevel level, String id, ItemStack output,
                                            Map<Integer, ItemStack> slots, boolean substitute) {
        var recipeId = new ResourceLocation("ae2lt_qa", id);
        CraftingRecipe holder = (CraftingRecipe) level.getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null) {
            int width = slots.keySet().stream().mapToInt(slot -> slot % 3 + 1).max().orElse(1);
            int height = slots.keySet().stream().mapToInt(slot -> slot / 3 + 1).max().orElse(1);
            var ingredients = NonNullList.withSize(width * height, net.minecraft.world.item.crafting.Ingredient.EMPTY);
            slots.forEach((slot, stack) -> ingredients.set(slot / 3 * width + slot % 3,
                    stack.is(Items.OAK_PLANKS)
                            ? net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.tags.ItemTags.PLANKS)
                            : net.minecraft.world.item.crafting.Ingredient.of(stack)));
            holder = new net.minecraft.world.item.crafting.ShapedRecipe(recipeId, "",
                    net.minecraft.world.item.crafting.CraftingBookCategory.MISC, width, height, ingredients, output);
            BigIntegerPipelineGameTests.installFixtureRecipe(level.getRecipeManager(), holder);
        }
        var inputs = new ItemStack[9];
        Arrays.fill(inputs, ItemStack.EMPTY);
        slots.forEach((slot, stack) -> inputs[slot] = stack);
        return PatternDetailsHelper.decodePattern(
                PatternDetailsHelper.encodeCraftingPattern(holder, inputs, output, substitute, substitute), level);
    }

    private static final class Disk implements MEStorage {
        final KeyCounter items = new KeyCounter();
        boolean blocked;
        Runnable beforeInsert = () -> {};
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (blocked) return 0;
            if (mode == Actionable.MODULATE) {
                beforeInsert.run();
                items.add(key, amount);
            }
            return amount;
        }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long taken = Math.min(amount, items.get(key));
            if (mode == Actionable.MODULATE) items.remove(key, taken);
            return taken;
        }
        @Override public void getAvailableStacks(KeyCounter out) {
            for (var entry : items) out.add(entry.getKey(), entry.getLongValue());
        }
        @Override public Component getDescription() { return Component.literal("Deferred return test storage"); }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        if (type == Optional.class) return Optional.empty();
        return null;
    }

    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> values.containsKey(method.getName())
                        ? values.get(method.getName()) : defaultValue(method.getReturnType())));
    }
}
