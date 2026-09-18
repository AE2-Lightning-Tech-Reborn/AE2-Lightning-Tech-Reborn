package com.moakiee.ae2lt.crafting.timewheel.allocation;

import com.moakiee.thunderbolt.core.crafting.batch.SharedBatchInputPattern;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.execution.CraftingCpuHelper;
import com.moakiee.ae2lt.crafting.timewheel.allocation.CraftingInputAllocation;
import com.moakiee.ae2lt.crafting.timewheel.allocation.CraftingInputAllocation.Choices;

class ExecutionInputAllocatorTest {
    private static final AEKey G = new TestKey("glass"), Q = new TestKey("quartz"),
            C = new TestKey("third"), X = new TestKey("intermediate"), Y = new TestKey("final");

    @Test
    void singleDispatchPreservesQuartzForDependentExactRecipe() {
        var flexible = pattern(X, input(Q, G));
        var exact = pattern(Y, input(X), input(Q));
        var tasks = tasks(flexible, 4, exact, 4);
        var allocator = new ExecutionInputAllocator(tasks, value -> value);
        var stock = stock(G, 4, Q, 4);
        for (int copy = 0; copy < 4; copy++) {
            var outputs = new KeyCounter();
            var taken = take(flexible, stock, allocator, outputs, new KeyCounter());
            assertNotNull(taken);
            assertEquals(1, taken[0].get(G));
            assertEquals(4, stock.list.get(Q));
            assertEquals(1, outputs.get(X));
            tasks.compute(flexible, (k, v) -> v - 1);
            stock.insert(X, 1, Actionable.MODULATE);
        }
        for (int copy = 0; copy < 4; copy++) {
            assertNotNull(take(exact, stock, allocator, new KeyCounter(), new KeyCounter()));
            tasks.compute(exact, (k, v) -> v - 1);
        }
        assertTrue(stock.list.isEmpty());
    }

    @Test
    void batchQuotaAndRejectedTailRemainTransactional() {
        var flexible = pattern(X, input(Q, G));
        var exact = pattern(Y, input(X), input(Q));
        var tasks = tasks(flexible, 4, exact, 4);
        var allocator = new ExecutionInputAllocator(tasks, value -> value);
        var stock = stock(G, 4, Q, 4);
        var first = bulk(flexible, stock, allocator, 4);
        assertNotNull(first);
        assertEquals(4, first.actualCopies);
        assertEquals(4, first.scaledInputs[0].get(G));
        assertEquals(4, stock.list.get(Q));
        TimeWheelInputExtractor.reinject(first, 2, stock);
        tasks.put(flexible, 2L); // Provider accepted only two copies.
        stock.insert(X, 2, Actionable.MODULATE);
        assertEquals(2, stock.list.get(G));
        var second = bulk(flexible, stock, allocator, 4);
        assertNotNull(second);
        assertEquals(2, second.actualCopies);
        tasks.put(flexible, 0L);
        stock.insert(X, 2, Actionable.MODULATE);
        var finish = bulk(exact, stock, allocator, 4);
        assertNotNull(finish);
        assertEquals(4, finish.actualCopies);
        assertTrue(stock.list.isEmpty());
    }

    @Test
    void intersectingFuzzySetsReassignSharedKeysAndSplitOneSlot() {
        var left = pattern(X, input(Q, G));
        var right = pattern(Y, new Input(2, null, new GenericStack(Q, 1), new GenericStack(C, 1)));
        var tasks = tasks(left, 1, right, 1);
        var allocator = new ExecutionInputAllocator(tasks, value -> value);
        var stock = stock(G, 1, Q, 1, C, 1);
        var first = take(left, stock, allocator, new KeyCounter(), new KeyCounter());
        assertNotNull(first);
        assertEquals(1, first[0].get(G));
        tasks.put(left, 0L);
        var second = take(right, stock, allocator, new KeyCounter(), new KeyCounter());
        assertNotNull(second);
        assertEquals(1, second[0].get(Q));
        assertEquals(1, second[0].get(C));
        assertTrue(stock.list.isEmpty());
    }

    @Test
    void exactAndFuzzySlotsWithinOnePatternKeepSeparateQuotas() {
        var combined = pattern(Y, input(Q, G), input(Q));
        var tasks = tasks(combined, 3);
        var stock = stock(G, 3, Q, 3);
        var result = bulk(combined, stock, new ExecutionInputAllocator(tasks, value -> value), 3);
        assertNotNull(result);
        assertEquals(3, result.actualCopies);
        assertEquals(3, result.scaledInputs[0].get(G));
        assertEquals(3, result.scaledInputs[1].get(Q));
        assertTrue(stock.list.isEmpty());
    }

    @Test
    void allocationIsRecomputedFromCurrentStockAndCanBeRebuiltAfterLoad() {
        var left = pattern(X, input(Q, G));
        var right = pattern(Y, input(Q, C));
        var tasks = tasks(left, 1, right, 2);
        var allocator = new ExecutionInputAllocator(tasks, value -> value);
        var before = stock(G, 1, Q, 1, C, 1);
        var allocation = allocator.allocate(left, before, null, 1);
        assertEquals(1L, allocation.openChoices().available(0, G, 1));
        var after = stock(Q, 2, C, 1);
        var changed = allocator.allocate(left, after, null, 1);
        assertEquals(1L, changed.openChoices().available(0, Q, 1));
        var restored = new ExecutionInputAllocator(new LinkedHashMap<>(tasks), value -> value);
        assertEquals(changed.openChoices().available(0, Q, 1),
                restored.allocate(left, after, null, 1).openChoices().available(0, Q, 1));
        assertEquals(2, after.list.get(Q)); // Simulations never reserve or consume physical stock.
    }

    @Test
    void missingAllocationConsumesNothingAndRegistersNoOutputs() {
        var flexible = pattern(X, input(Q, G));
        var exact = pattern(Y, input(Q));
        var stock = stock(Q, 1);
        var allocator = new ExecutionInputAllocator(tasks(flexible, 1, exact, 1), value -> value);
        var outputs = new KeyCounter();
        var containers = new KeyCounter();
        assertNull(take(flexible, stock, allocator, outputs, containers));
        assertEquals(1, stock.list.get(Q));
        assertTrue(outputs.isEmpty());
        assertTrue(containers.isEmpty());
    }

    @Test
    void failureInAnUnrelatedInputRollsBackAllocatedSlots() {
        var flexible = pattern(X, input(Q, G), input(C));
        var exact = pattern(Y, input(Q));
        var stock = stock(G, 1, Q, 1);
        var allocator = new ExecutionInputAllocator(tasks(flexible, 1, exact, 1), value -> value);
        assertNull(take(flexible, stock, allocator, new KeyCounter(), new KeyCounter()));
        assertEquals(1, stock.list.get(G));
        assertEquals(1, stock.list.get(Q));
        assertNull(bulk(flexible, stock, allocator, 1));
        assertEquals(1, stock.list.get(G));
    }

    @Test
    void futureReturnDoesNotReserveEveryLifetimeUseOfTheSeed() {
        var flexible = pattern(X, new Input(1, Q, new GenericStack(Q, 1), new GenericStack(G, 1)));
        var exact = pattern(Y, input(X), input(Q));
        var tasks = tasks(flexible, 1, exact, 1);
        var allocator = new ExecutionInputAllocator(tasks, value -> value);
        var stock = stock(Q, 1);
        var outputs = new KeyCounter();
        var containers = new KeyCounter();
        assertNotNull(take(flexible, stock, allocator, outputs, containers));
        assertEquals(1, containers.get(Q));
        tasks.put(flexible, 0L);
        stock.insert(X, 1, Actionable.MODULATE);
        stock.insert(Q, 1, Actionable.MODULATE);
        assertNotNull(take(exact, stock, allocator, new KeyCounter(), new KeyCounter()));
    }

    @Test
    void unrelatedMissingIntermediateDoesNotBlockAvailableRawMaterials() {
        var flexible = pattern(X, input(Q, G));
        var exact = pattern(Y, input(Q), input(C));
        var stock = stock(G, 1, Q, 1);
        var allocator = new ExecutionInputAllocator(tasks(flexible, 1, exact, 1), value -> value);
        var taken = take(flexible, stock, allocator, new KeyCounter(), new KeyCounter());
        assertNotNull(taken);
        assertEquals(1, taken[0].get(G));
    }

    @Test
    void capacitiesAreLongAmountsRatherThanOneNodePerItem() {
        long copies = 1_000_000_000_000L;
        var flexible = pattern(X, input(Q, G));
        var exact = pattern(Y, input(Q));
        var stock = stock(G, copies, Q, copies);
        var allocator = new ExecutionInputAllocator(tasks(flexible, copies, exact, copies), value -> value);
        var result = bulk(flexible, stock, allocator, copies);
        assertNotNull(result);
        assertEquals(copies, result.actualCopies);
        assertEquals(copies, stock.list.get(Q));
    }

    @Test
    void exactPhysicalUnitsAreReservedBeforeFlexibleUnits() {
        var flexible = pattern(X, new Input(1, null, new GenericStack(Q, 2), new GenericStack(G, 2)));
        var exact = pattern(Y, new Input(1, null, new GenericStack(Q, 3)));
        var stock = stock(G, 2, Q, 3);
        var allocator = new ExecutionInputAllocator(tasks(flexible, 1, exact, 1), value -> value);
        var taken = take(flexible, stock, allocator, new KeyCounter(), new KeyCounter());
        assertNotNull(taken);
        assertEquals(2, taken[0].get(G));
        assertEquals(3, stock.list.get(Q));
    }

    @Test
    void warmDispatchesReuseMembershipAndFlowWithoutScanningUnrelatedTasks() {
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        var flexible = pattern(X, counted(input(Q, G), checks));
        var exact = pattern(Y, counted(input(Q), checks));
        var tasks = tasks(flexible, 1_000, exact, 1_000);
        for (int i = 0; i < 10_000; i++) {
            tasks.put(pattern(new TestKey("out_" + i), counted(input(new TestKey("in_" + i)), checks)), 1L);
        }
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var allocator = new ExecutionInputAllocator(tasks, value -> { reads.incrementAndGet(); return value; }, true);
        var stock = stock(G, 1_000, Q, 1_000);
        var inventory = new CountingInventory(stock);
        long started = System.nanoTime();
        var first = allocator.allocate(flexible, inventory, null, 1);
        long coldNanos = System.nanoTime() - started;
        assertTrue(first.allowed());
        int coldChecks = checks.get(), coldQueries = inventory.queries;
        reads.set(0);
        started = System.nanoTime();
        for (int i = 0; i < 1_000; i++) {
            var allocation = allocator.allocate(flexible, inventory, null, 1);
            assertTrue(allocation.allowed());
            assertEquals(1_000L - i, allocation.openChoices().available(0, G, Long.MAX_VALUE));
            stock.extract(G, 1, Actionable.MODULATE);
            allocator.onInventoryChange(G);
            tasks.put(flexible, 999L - i);
        }
        long hotNanos = System.nanoTime() - started;
        assertEquals(coldChecks, checks.get(), "membership checks must not run again on known keys");
        assertEquals(coldQueries, inventory.queries, "known-key stock changes must not query fuzzy candidates");
        assertEquals(1, allocator.solveCount(), "normal consumption trims the previous feasible flow");
        assertTrue(reads.get() <= 4_000, "unrelated ten thousand tasks must stay out of the hot loop");
        System.out.printf("EXECUTION_CACHE unrelatedTasks=10000 dispatches=1000 fuzzyQueries=%d validityChecks=%d flowSolves=%d taskReads=%d coldMs=%.3f hotTotalMs=%.3f%n",
                inventory.queries, checks.get(), allocator.solveCount(), reads.get(), coldNanos / 1e6, hotNanos / 1e6);
    }

    @Test
    void unchangedInfeasibleAllocationIsCachedButNewVariantWakesIt() {
        var variant = new TestKey("quartz_component", "quartz");
        var baseInput = input(Q, G);
        var broad = new IPatternDetails.IInput() {
            @Override public GenericStack[] getPossibleInputs() { return baseInput.getPossibleInputs(); }
            @Override public long getMultiplier() { return 1; }
            @Override public boolean isValid(AEKey key, Level level) {
                return key.dropSecondary().equals(Q) || key.equals(G);
            }
            @Override public AEKey getRemainingKey(AEKey key) { return null; }
        };
        var flexible = pattern(X, broad);
        var exact = pattern(Y, input(Q));
        var allocator = new ExecutionInputAllocator(tasks(flexible, 1, exact, 1), value -> value, true);
        var inventory = new CountingInventory(stock(Q, 1));
        for (int i = 0; i < 100; i++) assertFalse(allocator.allocate(flexible, inventory, null, 1).allowed());
        assertEquals(1, allocator.solveCount());
        assertEquals(2, inventory.queries);
        inventory.delegate.insert(variant, 1, Actionable.MODULATE);
        allocator.onInventoryChange(variant);
        var allocation = allocator.allocate(flexible, inventory, null, 1);
        assertTrue(allocation.allowed());
        assertEquals(1L, allocation.openChoices().available(0, variant, 1));
        assertEquals(2, allocator.solveCount());
        assertEquals(2, inventory.queries, "new-key notification supplies the candidate without a rescan");
        var extracted = TimeWheelInputExtractor.extractPatternInputs(flexible, inventory.delegate, null,
                new KeyCounter(), new KeyCounter(), allocation);
        assertNotNull(extracted);
        assertEquals(1, extracted[0].get(variant));
        assertEquals(1, inventory.delegate.list.get(Q));
    }

    @Test
    void exactOnlyDispatchSkipsEvenAHeavilySharedKeyComponent() {
        var selected = pattern(X, input(Q));
        var tasks = tasks(selected, 1);
        for (int i = 0; i < 10_000; i++) tasks.put(pattern(new TestKey("out_" + i), input(Q)), 1L);
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var allocator = new ExecutionInputAllocator(tasks, value -> { reads.incrementAndGet(); return value; }, true);
        var inventory = new CountingInventory(stock(Q, 10_001));
        for (int i = 0; i < 1_000; i++) {
            var allocation = allocator.allocate(selected, inventory, null, 1);
            assertTrue(allocation.allowed());
            assertTrue(allocation.slotAllowances().isEmpty());
        }
        assertEquals(0, reads.get());
        assertEquals(0, allocator.solveCount());
        assertEquals(1, inventory.queries);
    }

    @Test
    void nonEquivalentAlternativeUnitsKeepNativeExtractionSemantics() {
        var flexible = pattern(X, new Input(1, null, new GenericStack(Q, 2), new GenericStack(G, 1)));
        var exact = pattern(Y, input(Q));
        var stock = stock(Q, 3, G, 1);
        var allocation = new ExecutionInputAllocator(tasks(flexible, 1, exact, 1), value -> value)
                .allocate(flexible, stock, null, 1);
        assertTrue(allocation.allowed());
        assertTrue(allocation.slotAllowances().isEmpty());
        assertEquals(3, stock.list.get(Q));
    }

    @Test
    void cachedWitnessDoesNotLockSurplusQuartzAwayFromFlexibleBatch() {
        var flexible = pattern(X, input(Q, G));
        var exact = pattern(Y, input(Q));
        var tasks = tasks(flexible, 4, exact, 4);
        var allocator = new ExecutionInputAllocator(tasks, value -> value);
        var stock = stock(G, 4, Q, 4);
        assertEquals(4L, allocator.allocate(flexible, stock, null, 4).openChoices().assigned(0, G));
        stock.insert(Q, 3, Actionable.MODULATE);
        var allocation = allocator.allocate(flexible, stock, null, 4);
        assertEquals(4L, allocation.openChoices().assigned(0, G), "the cached feasible witness is intentionally unchanged");
        var taken = bulkUsingNativeOrder(flexible, stock, allocator, 4);
        assertNotNull(taken);
        assertEquals(3, taken.actualCopies, "three surplus quartz may be chosen in one homogeneous batch");
        assertEquals(3, taken.scaledInputs[0].get(Q));
        assertEquals(4, stock.list.get(Q), "only the exact requirement must remain protected");
        assertEquals(4, stock.list.get(G));
        assertEquals(1, allocator.solveCount(), "surplus uses a residual adjustment, not another whole solve");
        TimeWheelInputExtractor.reinject(taken, 2, stock);
        tasks.put(flexible, 3L); // One accepted, two rejected; rejected flexible choices must come back.
        assertEquals(6, stock.list.get(Q));
        var retry = bulkUsingNativeOrder(flexible, stock, allocator, 3);
        assertNotNull(retry);
        assertEquals(2, retry.actualCopies);
        assertEquals(4, stock.list.get(Q));
    }

    @Test
    void successfulBatchWitnessSurvivesEveryRejectedTailWithoutResolving() {
        for (int accepted = 0; accepted <= 3; accepted++) {
            var selected = pattern(X, input(Q, G));
            var other = pattern(Y, input(Q, C));
            var tasks = tasks(selected, 4, other, 4);
            var allocator = new ExecutionInputAllocator(tasks, value -> value, true, true);
            var inventory = new ListCraftingInventory(allocator::onInventoryChange);
            inventory.insert(G, 4, Actionable.MODULATE);
            inventory.insert(Q, 4, Actionable.MODULATE);
            assertEquals(4, allocator.allocate(selected, inventory, null, 4).openChoices().assigned(0, G));
            inventory.insert(Q, 2, Actionable.MODULATE);
            inventory.insert(C, 1, Actionable.MODULATE);
            var batch = bulkUsingNativeOrder(selected, inventory, allocator, 4);
            assertNotNull(batch);
            assertEquals(3, batch.actualCopies, "failed upper trial must fall back to the jointly feasible batch");
            assertEquals(3, batch.scaledInputs[0].get(Q));
            TimeWheelInputExtractor.reinject(batch, 3 - accepted, inventory);
            tasks.put(selected, 4L - accepted);
            allocator.onTaskChange(selected);
            var saved = allocator.allocate(selected, inventory, null, 4).openChoices();
            assertNotNull(saved);
            assertEquals(3 - accepted, saved.assigned(0, Q), "keep the whole successful batch's exchange, not just its first copy");
            assertEquals(1, saved.assigned(0, G));
            assertEquals(1, allocator.solveCount());
            assertEquals(1, allocator.snapshotCount());
            var finishOther = bulk(other, inventory, allocator, 4);
            assertNotNull(finishOther, "the intersecting recipe must remain dispatchable");
        }
    }

    @Test
    void newlyAvailableAlternativeCanReleaseAnotherFuzzyConsumersQuota() {
        var selected = pattern(X, input(Q, G));
        var other = pattern(Y, input(Q, C));
        var tasks = tasks(selected, 1, other, 1);
        var allocator = new ExecutionInputAllocator(tasks, value -> value);
        var stock = stock(G, 1, Q, 1);
        allocator.allocate(selected, stock, null, 1); // Witness: selected gets G, other gets Q.
        stock.insert(C, 1, Actionable.MODULATE);
        var witness = allocator.allocate(selected, stock, null, 1);
        assertEquals(1L, witness.openChoices().assigned(0, G));
        var taken = takeUsingNativeOrder(selected, stock, allocator, new KeyCounter(), new KeyCounter());
        assertNotNull(taken);
        assertEquals(1, taken[0].get(Q), "other can move Q -> C, so selected need not stay bound to G");
        tasks.put(selected, 0L);
        var last = take(other, stock, allocator, new KeyCounter(), new KeyCounter());
        assertNotNull(last);
        assertEquals(1, last[0].get(C));
        assertEquals(1, allocator.solveCount());
    }

    @Test
    void individuallyOptionalKeysCannotBothBeReleasedFromAnIntersectingSet() {
        var selected = pattern(X, new Input(2, null,
                new GenericStack(G, 1), new GenericStack(Q, 1), new GenericStack(C, 1)));
        var other = pattern(Y, input(G, Q));
        var tasks = tasks(selected, 1, other, 1);
        var allocator = new ExecutionInputAllocator(tasks, value -> value);
        var stock = stock(G, 1, Q, 1, C, 1);
        var allocation = allocator.allocate(selected, stock, null, 1);
        var independent = allocation.openChoices();
        assertEquals(1, independent.available(0, G, 1));
        assertEquals(1, allocation.openChoices().available(0, Q, 1));
        independent.consume(0, G, 1);
        assertEquals(0, independent.available(0, Q, 1), "after spending G, Q becomes indispensable to the other demand");
        assertEquals(1, independent.available(0, C, 1));
        var taken = take(selected, stock, allocator, new KeyCounter(), new KeyCounter());
        assertNotNull(taken);
        assertEquals(1, taken[0].get(G));
        assertEquals(1, taken[0].get(C));
        assertEquals(1, stock.list.get(Q));
    }

    @Test
    void batchExpansionChecksAllSlotsTogetherRatherThanIndependentSafeMaxima() {
        var selected = pattern(X, input(G, C), input(Q, C));
        var other = pattern(Y, input(G, Q));
        var tasks = tasks(selected, 2, other, 1);
        var allocator = new ExecutionInputAllocator(tasks, value -> value);
        var stock = stock(G, 2, Q, 2, C, 1);
        var first = bulk(selected, stock, allocator, 2);
        assertNotNull(first);
        assertEquals(1, first.actualCopies, "a second G+Q copy would strand the other {G,Q} consumer");
        assertEquals(1, first.scaledInputs[0].get(G));
        assertEquals(1, first.scaledInputs[1].get(Q));
        tasks.put(selected, 1L);
        var second = take(selected, stock, allocator, new KeyCounter(), new KeyCounter());
        assertNotNull(second);
        assertEquals(1, second[0].get(G));
        assertEquals(1, second[1].get(C));
        tasks.put(selected, 0L);
        assertNotNull(take(other, stock, allocator, new KeyCounter(), new KeyCounter()));
        assertTrue(stock.list.isEmpty());
    }

    @Test
    void residualChoicesMatchAnIndependentExhaustiveFeasibilityOracle() {
        var random = new java.util.Random(417_920);
        var keys = new AEKey[]{G, Q, C};
        int feasible = 0;
        for (int example = 0; example < 1_500; example++) {
            int[] masks = new int[3], demand = new int[3], inventory = new int[3];
            for (int i = 0; i < 3; i++) {
                masks[i] = random.nextInt(7) + 1;
                demand[i] = random.nextInt(2) + 1;
                inventory[i] = random.nextInt(4);
            }
            int[][] witness = exhaustive(masks, demand.clone(), inventory.clone(), new int[3][3]);
            if (witness == null) continue;
            feasible++;
            var demands = new java.util.ArrayList<FlexibleInputChoices.Demand>();
            var stock = new LinkedHashMap<AEKey, Long>();
            for (int row = 0; row < 3; row++) {
                var options = new LinkedHashMap<AEKey, Long>();
                var assigned = new LinkedHashMap<AEKey, Long>();
                for (int key = 0; key < 3; key++) {
                    if ((masks[row] & (1 << key)) != 0) options.put(keys[key], 1L);
                    if (witness[row][key] > 0) assigned.put(keys[key], (long) witness[row][key]);
                    stock.put(keys[key], (long) inventory[key]);
                }
                demands.add(new FlexibleInputChoices.Demand(row, options, assigned));
            }
            var factory = FlexibleInputChoices.factory(demands, stock, 32_768);
            for (int row = 0; row < 3; row++) for (int key = 0; key < 3; key++) {
                long maximum = 0;
                if ((masks[row] & (1 << key)) != 0) {
                    for (int count = 1; count <= Math.min(demand[row], inventory[key]); count++) {
                        var remainingDemand = demand.clone();
                        var remainingStock = inventory.clone();
                        remainingDemand[row] -= count;
                        remainingStock[key] -= count;
                        if (exhaustive(masks, remainingDemand, remainingStock, new int[3][3]) != null) maximum = count;
                    }
                }
                var choices = factory.get();
                assertEquals(maximum, choices.available(row, keys[key], demand[row]),
                        "example=" + example + " row=" + row + " key=" + key);
                if (maximum > 0) {
                    choices.consume(row, keys[key], maximum);
                    assertEquals(0, choices.available(row, keys[key], 1));
                }
            }
            var sequence = factory.get();
            var remainingDemand = demand.clone();
            var remainingStock = inventory.clone();
            for (int step = 0; step < 6; step++) {
                int row = random.nextInt(3), key = random.nextInt(3);
                boolean safe = false;
                if (remainingDemand[row] > 0 && remainingStock[key] > 0 && (masks[row] & (1 << key)) != 0) {
                    remainingDemand[row]--;
                    remainingStock[key]--;
                    safe = exhaustive(masks, remainingDemand.clone(), remainingStock.clone(), new int[3][3]) != null;
                    remainingDemand[row]++;
                    remainingStock[key]++;
                }
                assertEquals(safe ? 1L : 0L, sequence.available(row, keys[key], 1));
                if (safe) {
                    sequence.consume(row, keys[key], 1);
                    remainingDemand[row]--;
                    remainingStock[key]--;
                }
            }
        }
        assertTrue(feasible > 200);
        System.out.println("RESIDUAL_ORACLE feasibleCases=" + feasible + " candidateQueries=" + feasible * 9);
    }

    @Test
    void exhaustedResidualBudgetKeepsTheProvenAllowanceInsteadOfReleasingReservedStock() {
        var demands = List.of(
                new FlexibleInputChoices.Demand(0, Map.of(G, 1L, Q, 1L), Map.of(G, 1L)),
                new FlexibleInputChoices.Demand(-1, Map.of(Q, 1L, C, 1L), Map.of(Q, 1L)));
        var stock = Map.of(G, 1L, Q, 1L, C, 1L);
        var exhausted = FlexibleInputChoices.factory(demands, stock, 0).get();
        assertEquals(0, exhausted.available(0, Q, 1));
        assertEquals(1, exhausted.available(0, G, 1));
        assertTrue(exhausted.exhausted());
        var factory = FlexibleInputChoices.factory(demands, stock, 32_768);
        var first = factory.get();
        assertEquals(1, first.available(0, Q, 1));
        first.consume(0, Q, 1);
        assertEquals(1, factory.get().available(0, Q, 1), "a cached factory opens isolated transactions");
    }

    @Test
    void failedExtractionRollsBackAnAdjustedChoiceAndCanRetry() {
        var missing = new TestKey("missing");
        var selected = pattern(X, input(Q, G), input(missing));
        var other = pattern(Y, input(Q, C));
        var tasks = tasks(selected, 1, other, 1);
        var allocator = new ExecutionInputAllocator(tasks, value -> value);
        var stock = stock(G, 1, Q, 1);
        allocator.allocate(selected, stock, null, 1);
        stock.insert(C, 1, Actionable.MODULATE);
        var outputs = new KeyCounter();
        assertNull(takeUsingNativeOrder(selected, stock, allocator, outputs, new KeyCounter()));
        assertEquals(1, stock.list.get(Q));
        assertEquals(1, stock.list.get(G));
        assertEquals(1, stock.list.get(C));
        assertTrue(outputs.isEmpty());
        stock.insert(missing, 1, Actionable.MODULATE);
        var retry = takeUsingNativeOrder(selected, stock, allocator, outputs, new KeyCounter());
        assertNotNull(retry);
        assertEquals(1, retry[0].get(Q));
        tasks.put(selected, 0L);
        assertNotNull(take(other, stock, allocator, new KeyCounter(), new KeyCounter()));
    }

    @Test
    void notifiedDispatchesTouchOnlyChangedRowsInsideOneLargeFuzzyComponent() {
        var tasks = new LinkedHashMap<IPatternDetails, Long>();
        for (int i = 0; i < 4_000; i++) tasks.put(pattern(new TestKey("out_" + i), input(G, Q)), 64L);
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var allocator = new ExecutionInputAllocator(tasks, value -> { reads.incrementAndGet(); return value; }, true, true);
        var inventory = new ListCraftingInventory(allocator::onInventoryChange);
        inventory.insert(G, 4_000 * 64, Actionable.MODULATE);
        inventory.insert(Q, 64, Actionable.MODULATE);
        var first = tasks.keySet().iterator().next();
        allocator.allocate(first, inventory, null, 1);
        int initialReads = reads.get();
        long initialStockReads = allocator.stockReadCount();
        int dispatched = 0;
        for (var pattern : tasks.keySet()) {
            var allocation = allocator.allocate(pattern, inventory, null, 1);
            var choices = (FlexibleInputChoices) allocation.openChoices();
            assertEquals(0, choices.touchedRows(), "opening a transaction must not copy the conflict group");
            AEKey chosen = choices.assigned(0, G) > 0 ? G : Q;
            assertEquals(1, choices.available(0, chosen, 1));
            choices.consume(0, chosen, 1);
            assertEquals(1, choices.touchedRows());
            choices.retainAssignments();
            assertEquals(1, inventory.extract(chosen, 1, Actionable.MODULATE));
            tasks.compute(pattern, (k, v) -> v - 1);
            allocator.onTaskChange(pattern);
            if (++dispatched == 1_000) break;
        }
        allocator.allocate(first, inventory, null, 1); // Reconcile the last accepted copy.
        assertEquals(1, allocator.solveCount());
        assertEquals(1, allocator.snapshotCount());
        assertEquals(1_000, allocator.trimmedRowCount());
        assertEquals(1_000, reads.get() - initialReads);
        assertEquals(1_000, allocator.stockReadCount() - initialStockReads);
        System.out.println("INCREMENTAL_COMPONENT rows=4000 dispatches=1000 snapshots=1 solves=1 taskReads=1000 stockReads=1000");
    }

    @Test
    void notifiedMissingMaterialsDoNotRescanWhileTheCpuWaits() {
        var selected = pattern(X, input(Q, G));
        var exact = pattern(Y, input(Q));
        var tasks = tasks(selected, 1, exact, 1);
        var allocator = new ExecutionInputAllocator(tasks, value -> value, true, true);
        var inventory = new ListCraftingInventory(allocator::onInventoryChange);
        inventory.insert(Q, 1, Actionable.MODULATE);
        for (int i = 0; i < 1_000; i++) assertFalse(allocator.allocate(selected, inventory, null, 1).allowed());
        assertEquals(1, allocator.snapshotCount());
        assertEquals(1, allocator.solveCount());
        inventory.insert(G, 1, Actionable.MODULATE);
        assertTrue(allocator.allocate(selected, inventory, null, 1).allowed());
        assertEquals(2, allocator.snapshotCount());
    }

    @Test
    void speculativeCheckpointRestoresOnlyTouchedRowsAndKeepsSessionsIsolated() {
        var demands = new java.util.ArrayList<FlexibleInputChoices.Demand>();
        demands.add(new FlexibleInputChoices.Demand(0, Map.of(G, 1L, Q, 1L), Map.of(G, 2L)));
        for (int i = 1; i < 10_000; i++) {
            demands.add(new FlexibleInputChoices.Demand(-1, Map.of(G, 1L, Q, 1L), Map.of(G, 1L)));
        }
        var factory = FlexibleInputChoices.factory(demands, Map.of(G, 10_001L, Q, 2L), 32_768);
        var choices = (FlexibleInputChoices) factory.get();
        assertEquals(0, choices.touchedRows());
        var rollback = choices.checkpoint();
        assertEquals(2, choices.available(0, Q, 2));
        choices.consume(0, Q, 1);
        var nested = choices.checkpoint();
        choices.consume(0, Q, 1);
        nested.run();
        assertEquals(1, choices.assigned(0, Q));
        assertEquals(1, choices.touchedRows());
        rollback.run();
        assertEquals(0, choices.touchedRows());
        assertEquals(2, choices.assigned(0, G));
        assertEquals(2, choices.assigned(0, Q), "the restored bucket also backs this interchangeable key");
        var independent = factory.get();
        choices.consume(0, G, 1);
        assertEquals(2, independent.assigned(0, G));
        assertEquals(1, choices.assigned(0, G));
    }

    @Test
    void notifiedPartialAcceptanceAndFullRejectionReconcileActualQuantities() {
        var flexible = pattern(X, input(Q, G));
        var exact = pattern(Y, input(Q));
        var tasks = tasks(flexible, 4, exact, 4);
        var allocator = new ExecutionInputAllocator(tasks, value -> value, true, true);
        var inventory = new ListCraftingInventory(allocator::onInventoryChange);
        inventory.insert(G, 4, Actionable.MODULATE);
        inventory.insert(Q, 4, Actionable.MODULATE);
        var rejected = bulk(flexible, inventory, allocator, 4);
        assertNotNull(rejected);
        TimeWheelInputExtractor.reinject(rejected, 4, inventory);
        var partial = bulk(flexible, inventory, allocator, 4);
        assertNotNull(partial);
        TimeWheelInputExtractor.reinject(partial, 2, inventory);
        tasks.put(flexible, 2L);
        allocator.onTaskChange(flexible);
        var finish = bulk(flexible, inventory, allocator, 4);
        assertNotNull(finish);
        assertEquals(2, finish.actualCopies);
        tasks.remove(flexible);
        allocator.onTaskChange(flexible);
        assertNotNull(bulk(exact, inventory, allocator, 4));
        assertEquals(1, allocator.snapshotCount());
        assertEquals(1, allocator.solveCount());
        assertEquals(0, inventory.list.get(G));
        assertEquals(0, inventory.list.get(Q));
    }

    @Test
    void taskGrowthAndUnknownVariantsInvalidateIncrementalWitness() {
        var selected = pattern(X, input(Q, G));
        var exact = pattern(Y, input(Q));
        var tasks = tasks(selected, 1, exact, 1);
        var allocator = new ExecutionInputAllocator(tasks, value -> value, true, true);
        var inventory = new ListCraftingInventory(allocator::onInventoryChange);
        inventory.insert(G, 1, Actionable.MODULATE);
        inventory.insert(Q, 1, Actionable.MODULATE);
        assertTrue(allocator.allocate(selected, inventory, null, 1).allowed());
        tasks.put(exact, 2L);
        allocator.onTaskChange(exact);
        assertFalse(allocator.allocate(selected, inventory, null, 1).allowed());
        inventory.insert(Q, 1, Actionable.MODULATE);
        assertTrue(allocator.allocate(selected, inventory, null, 1).allowed());
        long snapshots = allocator.snapshotCount();
        inventory.insert(new TestKey("quartz_components", "quartz"), 1, Actionable.MODULATE);
        assertTrue(allocator.allocate(selected, inventory, null, 1).allowed());
        assertEquals(snapshots + 1, allocator.snapshotCount());
    }

    @Test
    void changingOpaqueReservationViewIsNeverTreatedAsAnEmptyMap() {
        var selected = pattern(X, input(Q, G));
        var tasks = tasks(selected, 2);
        var allocator = new ExecutionInputAllocator(tasks, value -> value, true, true);
        var inventory = new ListCraftingInventory(allocator::onInventoryChange);
        inventory.insert(G, 2, Actionable.MODULATE);
        inventory.insert(Q, 2, Actionable.MODULATE);
        var reserved = new java.util.AbstractMap<AEKey, Long>() {
            @Override public Long get(Object key) { return key.equals(Q) ? 2L : null; }
            @Override public java.util.Set<Entry<AEKey, Long>> entrySet() { return java.util.Set.of(); }
        };
        assertTrue(reserved.isEmpty(), "this live reservation API intentionally has no enumerable entries");
        var taken = TimeWheelInputExtractor.bulkExtract(selected, inventory, 2, false, reserved, null,
                (visible, copies) -> allocator.allocate(selected, visible, null, copies));
        assertNotNull(taken);
        assertEquals(2, taken.scaledInputs[0].get(G));
        assertEquals(2, inventory.list.get(Q));
    }

    @Test
    void intersectingSetReservesMustProtectTheirUnion() {
        var selected = pattern(X, input(Q, new TestKey("spare")));
        var left = pattern(new TestKey("left"), new Input(2, null, new GenericStack(G, 1), new GenericStack(Q, 1)));
        var right = pattern(Y, new Input(2, null, new GenericStack(Q, 1), new GenericStack(C, 1)));
        var tasks = tasks(selected, 1, left, 1, right, 1);
        var allocator = new ExecutionInputAllocator(tasks, value -> value, true, true);
        var inventory = new ListCraftingInventory(allocator::onInventoryChange);
        inventory.insert(G, 1, Actionable.MODULATE);
        inventory.insert(Q, 2, Actionable.MODULATE);
        inventory.insert(C, 1, Actionable.MODULATE);
        inventory.insert(new TestKey("spare"), 1, Actionable.MODULATE);
        var choices = allocator.allocate(selected, inventory, null, 1).openChoices();
        assertEquals(0, choices.available(0, Q, 1), "both individual sets have slack, but their union has none");
        assertEquals(1, choices.available(0, new TestKey("spare"), 1));
    }

    @Test
    void notifiedSequentialSelectionsMatchIndependentExhaustiveOracle() {
        var random = new java.util.Random(5793);
        AEKey[] keys = {G, Q, C};
        int cases = 0, steps = 0;
        for (int attempt = 0; attempt < 1_000; attempt++) {
            int[] masks = {1 + random.nextInt(7), 1 + random.nextInt(7), 1 + random.nextInt(7)};
            int[] demand = {1 + random.nextInt(3), 1 + random.nextInt(3), 1 + random.nextInt(3)};
            int[] counts = {random.nextInt(5), random.nextInt(5), random.nextInt(5)};
            if (exhaustive(masks, demand.clone(), counts.clone(), new int[3][3]) == null) continue;
            cases++;
            var patterns = new IPatternDetails[3];
            var tasks = new LinkedHashMap<IPatternDetails, Long>();
            for (int row = 0; row < 3; row++) {
                var accepted = new java.util.ArrayList<AEKey>();
                for (int key = 0; key < 3; key++) if ((masks[row] & 1 << key) != 0) accepted.add(keys[key]);
                patterns[row] = pattern(new TestKey("output_" + row), input(accepted.toArray(AEKey[]::new)));
                tasks.put(patterns[row], (long) demand[row]);
            }
            var allocator = new ExecutionInputAllocator(tasks, value -> value, true, true);
            var inventory = new ListCraftingInventory(allocator::onInventoryChange);
            for (int key = 0; key < 3; key++) inventory.insert(keys[key], counts[key], Actionable.MODULATE);
            while (demand[0] + demand[1] + demand[2] > 0) {
                int row = random.nextInt(3);
                if (demand[row] == 0) continue;
                var allocation = allocator.allocate(patterns[row], inventory, null, 1);
                assertTrue(allocation.allowed());
                int chosen = -1;
                for (int key = 0; key < 3; key++) {
                    if ((masks[row] & 1 << key) == 0 || counts[key] == 0) continue;
                    demand[row]--; counts[key]--;
                    boolean safe = exhaustive(masks, demand.clone(), counts.clone(), new int[3][3]) != null;
                    demand[row]++; counts[key]++;
                    var choices = allocation.openChoices();
                    long allowed = choices == null || !choices.managesSlot(0) ? 1 : choices.available(0, keys[key], 1);
                    assertEquals(safe ? 1 : 0, allowed, "incremental matching must preserve all feasible substitutions");
                    if (safe) chosen = key;
                }
                assertTrue(chosen >= 0);
                var committed = allocation.openChoices();
                if (committed != null && committed.managesSlot(0)) {
                    assertEquals(1, committed.available(0, keys[chosen], 1));
                    committed.consume(0, keys[chosen], 1);
                    committed.retainAssignments();
                }
                assertEquals(1, inventory.extract(keys[chosen], 1, Actionable.MODULATE));
                demand[row]--; counts[chosen]--;
                tasks.put(patterns[row], (long) demand[row]);
                allocator.onTaskChange(patterns[row]);
                steps++;
            }
        }
        assertTrue(cases > 200);
        System.out.println("INCREMENTAL_ORACLE feasibleCases=" + cases + " acceptedCopies=" + steps);
    }

    @Test
    void wideCandidateDomainUsesOneBucketWithoutADenseSlotKeyMatrix() {
        AEKey[] keys = new AEKey[10_000];
        var inventory = new ListCraftingInventory(key -> { });
        for (int i = 0; i < keys.length; i++) {
            keys[i] = new TestKey("wide_" + i);
            inventory.insert(keys[i], 1, Actionable.MODULATE);
        }
        var left = pattern(X, input(keys));
        var right = pattern(Y, input(keys));
        var allocator = new ExecutionInputAllocator(tasks(left, 5_000, right, 5_000), value -> value, true, true);
        var allocation = allocator.allocate(left, inventory, null, 1);
        assertTrue(allocation.allowed());
        var choices = (FlexibleInputChoices) allocation.openChoices();
        assertEquals(1, choices.bucketCount());
        long before = choices.remainingWork();
        assertEquals(1, choices.available(0, keys[9_999], 1));
        assertEquals(before, choices.remainingWork());
    }

    @Test
    void wideFlexibleComponentCannotLatchAnExactTaskIntoPermanentWait() {
        AEKey[] keys = new AEKey[17_000];
        for (int i = 0; i < keys.length; i++) keys[i] = new TestKey("capacity_" + i);
        var flexible = pattern(X, input(keys));
        var otherFlexible = pattern(Y, input(keys));
        var exact = pattern(C, input(keys[0]));
        var tasks = tasks(flexible, 1, otherFlexible, 1, exact, 1);
        var allocator = new ExecutionInputAllocator(tasks, value -> value, true, true);
        var inventory = new ListCraftingInventory(allocator::onInventoryChange);
        inventory.insert(keys[0], 1, Actionable.MODULATE);
        inventory.insert(keys[1], 2, Actionable.MODULATE);
        assertFalse(allocator.allocate(flexible, inventory, null, 1).allowed(),
                "the deliberately oversized ambiguous search must respect its memory bound");
        assertTrue(allocator.allocate(exact, inventory, null, 1).allowed(),
                "an exact forced choice needs no wide matching and must still make progress");
    }

    @Test
    void completedRowsDoNotKeepRemainingWorkOverTheCapacityBound() {
        AEKey[] keys = new AEKey[17_000];
        for (int i = 0; i < keys.length; i++) keys[i] = new TestKey("completed_capacity_" + i);
        var flexible = pattern(X, input(keys));
        var completed = pattern(Y, input(keys));
        var exact = pattern(C, input(keys[0]));
        var tasks = tasks(flexible, 1, completed, 1, exact, 1);
        var allocator = new ExecutionInputAllocator(tasks, value -> value, true, true);
        var inventory = new ListCraftingInventory(allocator::onInventoryChange);
        inventory.insert(keys[0], 1, Actionable.MODULATE);
        inventory.insert(keys[1], 2, Actionable.MODULATE);
        assertFalse(allocator.allocate(flexible, inventory, null, 1).allowed());
        tasks.remove(completed);
        allocator.onTaskChange(completed);
        assertTrue(allocator.allocate(flexible, inventory, null, 1).allowed(),
                "task completion alone must invalidate the capacity failure");
    }

    @Test
    void pendingSparseSolveResumesAndRevalidatesAnArrivalOrRemoval() {
        int size = 700;
        AEKey[] keys = new AEKey[size + 1];
        var tasks = new LinkedHashMap<IPatternDetails, Long>();
        for (int i = 0; i <= size; i++) keys[i] = new TestKey("chain_input_" + i);
        for (int i = 0; i < size; i++) tasks.put(pattern(new TestKey("chain_output_" + i), input(keys[i], keys[i + 1])), 1L);
        var selected = tasks.keySet().iterator().next();
        var allocator = new ExecutionInputAllocator(tasks, value -> value, true, true);
        var inventory = new ListCraftingInventory(allocator::onInventoryChange);
        for (var key : keys) inventory.insert(key, 1, Actionable.MODULATE);
        assertFalse(allocator.allocate(selected, inventory, null, 1).allowed(), "work slice must yield on a large initial match");
        inventory.extract(keys[0], 1, Actionable.MODULATE);
        int attempts = 0;
        var allocation = allocator.allocate(selected, inventory, null, 1);
        while (!allocation.allowed()) {
            assertTrue(++attempts < 100, "pending is not cached as permanent missing stock");
            allocation = allocator.allocate(selected, inventory, null, 1);
        }
        assertEquals(2, allocator.solveCount(), "one initial snapshot and one changed-stock snapshot");
        assertEquals(2, allocator.snapshotCount(), "unchanged resumed slices do not rescan candidates");
        assertEquals(0, allocation.openChoices().available(0, keys[0], 1));
        assertEquals(1, allocation.openChoices().available(0, keys[1], 1));

        var wrappedAllocator = new ExecutionInputAllocator(tasks, value -> value, true, true);
        attempts = 0;
        var wrapped = wrappedAllocator.allocate(selected, new CountingInventory(inventory), null, 1);
        while (!wrapped.allowed()) {
            assertTrue(++attempts < 100, "fresh equivalent reservation wrappers must not restart the solver");
            wrapped = wrappedAllocator.allocate(selected, new CountingInventory(inventory), null, 1);
        }
        assertEquals(1, wrappedAllocator.solveCount());
    }

    // Tiny integer reference search: enumerate every legal one-unit placement independently of the
    // production residual graph. Test instances have at most six units, so this is deliberately exhaustive.
    private static int[][] exhaustive(int[] masks, int[] demand, int[] stock, int[][] assignment) {
        int row = 0;
        while (row < demand.length && demand[row] == 0) row++;
        if (row == demand.length) {
            var result = new int[assignment.length][];
            for (int i = 0; i < result.length; i++) result[i] = assignment[i].clone();
            return result;
        }
        for (int key = 0; key < stock.length; key++) {
            if (stock[key] <= 0 || (masks[row] & (1 << key)) == 0) continue;
            demand[row]--;
            stock[key]--;
            assignment[row][key]++;
            var result = exhaustive(masks, demand, stock, assignment);
            assignment[row][key]--;
            stock[key]++;
            demand[row]++;
            if (result != null) return result;
        }
        return null;
    }

    private static IPatternDetails.IInput counted(IPatternDetails.IInput input,
            java.util.concurrent.atomic.AtomicInteger checks) {
        return new IPatternDetails.IInput() {
            @Override public GenericStack[] getPossibleInputs() { return input.getPossibleInputs(); }
            @Override public long getMultiplier() { return input.getMultiplier(); }
            @Override public boolean isValid(AEKey key, Level level) { checks.incrementAndGet(); return input.isValid(key, level); }
            @Override public AEKey getRemainingKey(AEKey key) { return input.getRemainingKey(key); }
        };
    }
    private static final class CountingInventory implements appeng.crafting.inv.ICraftingInventory {
        final ListCraftingInventory delegate;
        int queries;
        CountingInventory(ListCraftingInventory delegate) { this.delegate = delegate; }
        @Override public void insert(AEKey key, long amount, Actionable mode) { delegate.insert(key, amount, mode); }
        @Override public long extract(AEKey key, long amount, Actionable mode) { return delegate.extract(key, amount, mode); }
        @Override public Iterable<AEKey> findFuzzyTemplates(AEKey key) { queries++; return delegate.findFuzzyTemplates(key); }
    }

    private static KeyCounter[] take(IPatternDetails pattern, ListCraftingInventory stock,
            ExecutionInputAllocator allocator, KeyCounter outputs, KeyCounter containers) {
        return TimeWheelInputExtractor.extractPatternInputs(pattern, stock, null, outputs, containers,
                allocator.allocate(pattern, stock, null, 1));
    }

    // These tests deliberately exercise the substitution fallback, even when the new positive
    // index could fill a whole copy without any rearrangement. This keeps their rollback and
    // surplus-exchange assertions meaningful instead of merely asserting a candidate order.
    private record WithoutHints(Choices delegate) implements Choices {
        @Override public boolean managesSlot(int slot) { return delegate.managesSlot(slot); }
        @Override public long assigned(int slot, AEKey key) { return delegate.assigned(slot, key); }
        @Override public long available(int slot, AEKey key, long amount) { return delegate.available(slot, key, amount); }
        @Override public void consume(int slot, AEKey key, long amount) { delegate.consume(slot, key, amount); }
        @Override public Choices copy() { return new WithoutHints(delegate.copy()); }
        @Override public Runnable checkpoint() { return delegate.checkpoint(); }
        @Override public boolean exhausted() { return delegate.exhausted(); }
        @Override public void retainAssignments() { delegate.retainAssignments(); }
    }
    private static CraftingInputAllocation withoutHints(CraftingInputAllocation allocation) {
        return allocation.choiceFactory() == null ? allocation : new CraftingInputAllocation(
                allocation.allowed(), allocation.slotAllowances(), () -> new WithoutHints(allocation.openChoices()));
    }
    private static KeyCounter[] takeUsingNativeOrder(IPatternDetails pattern, ListCraftingInventory stock,
            ExecutionInputAllocator allocator, KeyCounter outputs, KeyCounter containers) {
        return TimeWheelInputExtractor.extractPatternInputs(pattern, stock, null, outputs, containers,
                withoutHints(allocator.allocate(pattern, stock, null, 1)));
    }
    private static TimeWheelInputExtractor.BulkResult bulkUsingNativeOrder(IPatternDetails pattern,
            ListCraftingInventory stock, ExecutionInputAllocator allocator, long copies) {
        return TimeWheelInputExtractor.bulkExtract(pattern, stock, copies, true, Map.of(), null,
                (visible, budget) -> withoutHints(allocator.allocate(pattern, visible, null, budget)));
    }
    private static TimeWheelInputExtractor.BulkResult bulk(IPatternDetails pattern,
            ListCraftingInventory stock, ExecutionInputAllocator allocator, long copies) {
        return TimeWheelInputExtractor.bulkExtract(pattern, stock, copies, true, Map.of(), null,
                (visible, budget) -> allocator.allocate(pattern, visible, null, budget));
    }
    private static Input input(AEKey... keys) {
        var possible = new GenericStack[keys.length];
        for (int i = 0; i < keys.length; i++) possible[i] = new GenericStack(keys[i], 1);
        return new Input(1, null, possible);
    }
    private record Input(long multiplier, AEKey returned, GenericStack... possible) implements IPatternDetails.IInput {
        @Override public GenericStack[] getPossibleInputs() { return possible; }
        @Override public long getMultiplier() { return multiplier; }
        @Override public boolean isValid(AEKey key, Level level) {
            for (var candidate : possible) if (candidate.what().equals(key)) return true;
            return false;
        }
        @Override public AEKey getRemainingKey(AEKey key) { return returned; }
    }
    private static IPatternDetails pattern(AEKey output, IPatternDetails.IInput... inputs) {
        return new Pattern(output, inputs);
    }
    private record Pattern(AEKey output, IInput[] inputs) implements IPatternDetails {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { return inputs; }
        @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(output, 1)); }
    }
    private static Map<IPatternDetails, Long> tasks(Object... entries) {
        var result = new LinkedHashMap<IPatternDetails, Long>();
        for (int i = 0; i < entries.length; i += 2) result.put((IPatternDetails) entries[i], ((Number) entries[i + 1]).longValue());
        return result;
    }
    private static ListCraftingInventory stock(Object... entries) {
        var result = new ListCraftingInventory(key -> {});
        for (int i = 0; i < entries.length; i += 2) result.insert((AEKey) entries[i], ((Number) entries[i + 1]).longValue(), Actionable.MODULATE);
        return result;
    }

    static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();
        private final String id;
        private final String primary;

        TestKey(String id) { this(id, id); }
        TestKey(String id, String primary) { this.id = id; this.primary = primary.intern(); }

        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() { return id.equals(primary) ? this : new TestKey(primary); }
        @Override public CompoundTag toTag(net.minecraft.core.HolderLookup.Provider registries) {
            var tag = new CompoundTag();
            tag.putString("id", id);
            return tag;
        }
        @Override public Object getPrimaryKey() { return primary; }
        @Override public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("thunderbolt_test", id);
        }
        @Override public void writeToPacket(RegistryFriendlyByteBuf data) { }
        @Override protected Component computeDisplayName() { return Component.literal(id); }
        @Override public void addDrops(
                long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
        @Override public boolean hasComponents() { return false; }
        @Override public boolean equals(Object obj) {
            return obj instanceof TestKey other && id.equals(other.id);
        }
        @Override public int hashCode() { return id.hashCode(); }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(ResourceLocation.fromNamespaceAndPath("thunderbolt_test", "simulation_key"),
                    TestKey.class, Component.literal("simulation key"));
        }
        @Override public MapCodec<? extends AEKey> codec() { return null; }
        @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) { return null; }
    }
}
