package com.moakiee.ae2lt.crafting.timewheel.allocation;

import com.moakiee.thunderbolt.core.crafting.batch.SharedBatchInputPattern;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;

import com.moakiee.ae2lt.crafting.timewheel.allocation.CraftingInputAllocation;

class InputAllocationHotPathTest {
    private static final AEKey A = key("a"), B = key("b"), C = key("c");

    @Test
    void savedExchangeRemainsFeasibleForEveryPartialAcceptanceIncludingFullRejection() {
        for (int accepted = 0; accepted <= 10; accepted++) {
            var graph = crossing(10, 10, 10);
            var choices = graph.factory(Map.of(0, 0), 10_000).get();
            assertEquals(10, choices.available(0, B, 10));
            choices.consume(0, B, 10);
            choices.retainAssignments();
            assertEquals(Map.of(B, 10L), graph.assigned.get(0));
            assertEquals(Map.of(C, 10L), graph.assigned.get(1));
            assertEquals(10, graph.required[0], "publication does not commit provisional consumption");
            assertEquals(10, graph.buckets.physicalStock.get(B));
            graph.setStock(B, 10 - accepted);
            assertTrue(graph.trim(0, 10 - accepted));
            assertFeasible(graph);
            var next = (FlexibleInputChoices) graph.factory(Map.of(0, 0, 1, 1), 0).get();
            assertEquals(10 - accepted, next.available(0, B, 10));
            assertEquals(10, next.available(1, C, 10));
            assertEquals(0, next.remainingWork(), "the saved exchange needs no repeat residual search");
        }
    }

    @Test
    void rollbackRestoresCandidateCursorAndDoesNotPublishSpeculativeConsumption() {
        var options = ordered(A, B);
        var graph = new FlexibleInputChoices.Graph(List.of(
                new FlexibleInputChoices.Demand(0, options, Map.of(A, 1L)),
                new FlexibleInputChoices.Demand(1, options, Map.of(B, 1L))), Map.of(A, 1L, B, 1L));
        var choices = graph.factory(Map.of(0, 0, 1, 1), 100).get();
        assertEquals(A, choices.preferredInputs(0).iterator().next().what());
        var rollback = choices.checkpoint();
        choices.consume(0, A, 1);
        assertEquals(B, choices.preferredInputs(1).iterator().next().what());
        assertThrows(IllegalStateException.class, choices::retainAssignments);
        rollback.run();
        assertEquals(A, choices.preferredInputs(0).iterator().next().what());
        choices.retainAssignments();
        assertTrue(graph.recentlyConsumed.isEmpty());
        assertFeasible(graph);
    }

    @Test
    void staleProofAndItsCopyCannotOverwriteANewerAssignment() {
        var graph = crossing(1, 1, 1);
        var stale = graph.factory(Map.of(0, 0), 100).get();
        var current = graph.factory(Map.of(0, 0), 100).get();
        assertEquals(1, stale.available(0, B, 1));
        assertEquals(1, current.available(0, B, 1));
        current.consume(0, B, 1);
        current.retainAssignments();
        graph.setStock(B, 0);
        assertTrue(graph.trim(0, 0));
        stale.copy().retainAssignments();
        stale.retainAssignments();
        assertTrue(graph.assigned.get(0).isEmpty());
        assertFeasible(graph);
    }

    @Test
    void tenThousandOutOfOrderConsumptionsDoNotScanOtherPositiveOrExhaustedEdges() {
        int size = 10_000;
        var options = new LinkedHashMap<AEKey, Long>();
        var stock = new LinkedHashMap<AEKey, Long>();
        var keys = new ArrayList<AEKey>();
        for (int i = 0; i < size; i++) {
            var key = key("distinct_bucket_" + i);
            keys.add(key); options.put(key, 1L); stock.put(key, 1L);
        }
        var rows = new ArrayList<FlexibleInputChoices.Demand>();
        rows.add(new FlexibleInputChoices.Demand(0, options, stock));
        // Zero-demand exact rows distinguish signatures; every actual key has degree two.
        for (var key : keys) rows.add(new FlexibleInputChoices.Demand(-1, Map.of(key, 1L), Map.of()));
        var graph = new FlexibleInputChoices.Graph(rows, stock);
        for (int i = size - 1; i >= 0; i--) {
            var choices = graph.factory(Map.of(0, 0), 0).get();
            var key = keys.get(i);
            assertEquals(1, choices.available(0, key, 1));
            choices.consume(0, key, 1);
            choices.retainAssignments();
            graph.setStock(key, 0);
            assertTrue(graph.trim(0, i));
            assertEquals(i, graph.assigned.get(0).size(), "zero edges must be unlinked immediately");
        }
        assertEquals(size, graph.trimVisits, "one touched edge per dispatch, independent of candidate width");
        assertTrue(graph.buckets.liveKeys.values().stream().allMatch(index -> index.size() == 0));
        assertFeasible(graph);
    }

    @Test
    void fourThousandRealHelperDispatchesUseOneCandidateEachWithoutFuzzyRediscovery() {
        int size = 4_000;
        var options = new LinkedHashMap<AEKey, Long>();
        var stock = new LinkedHashMap<AEKey, Long>();
        var checks = new AtomicInteger();
        for (int i = 0; i < size; i++) {
            var key = key("variant_" + i);
            options.put(key, 1L); stock.put(key, 1L);
        }
        var firstKey = options.keySet().iterator().next();
        var graph = new FlexibleInputChoices.Graph(List.of(
                new FlexibleInputChoices.Demand(0, options, Map.of(firstKey, (long) size / 2)),
                new FlexibleInputChoices.Demand(1, options, Map.of(firstKey, (long) size / 2))), stock);
        var inventory = new CountingInventory();
        stock.forEach((key, amount) -> inventory.insert(key, amount, Actionable.MODULATE));
        var pattern = pattern(options, checks);
        long candidates = 0;
        for (int i = 0; i < size; i++) {
            int row = i % 2;
            var opened = new AtomicReference<FlexibleInputChoices>();
            var allocation = new CraftingInputAllocation(true, Map.of(0, Map.of()), () -> {
                var choices = (FlexibleInputChoices) graph.factory(Map.of(0, row), 0).get();
                opened.set(choices); return choices;
            });
            var extracted = TimeWheelInputExtractor.extractPatternInputs(pattern, inventory, null,
                    new KeyCounter(), new KeyCounter(), allocation);
            assertNotNull(extracted);
            candidates += opened.get().candidateVisits();
            for (var entry : extracted[0]) graph.setStock(entry.getKey(), inventory.delegate.list.get(entry.getKey()));
            assertTrue(graph.trim(row, graph.required[row] - 1));
        }
        assertEquals(size, candidates);
        assertEquals(size, checks.get(), "revalidate only the actual candidate on the warm path");
        assertEquals(0, inventory.fuzzyQueries, "do not open AE2's eager full-template discovery");
        assertEquals(size, graph.trimVisits);
        assertTrue(graph.assigned.stream().allMatch(Map::isEmpty));
        assertTrue(graph.buckets.liveKeys.values().stream().allMatch(index -> index.size() == 0));
        assertTrue(inventory.delegate.list.isEmpty());
    }

    @Test
    void sharedBucketCursorSkipsConsumedKeysOnceAcrossSlotsAndReappearanceIsIndexed() {
        var options = new LinkedHashMap<AEKey, Long>();
        var stock = new LinkedHashMap<AEKey, Long>();
        for (int i = 0; i < 1_000; i++) {
            var key = key("cursor_" + i);
            options.put(key, 1L); stock.put(key, 1L);
        }
        var seed = options.keySet().iterator().next();
        var graph = new FlexibleInputChoices.Graph(List.of(
                new FlexibleInputChoices.Demand(0, options, Map.of(seed, 500L)),
                new FlexibleInputChoices.Demand(1, options, Map.of(seed, 500L))), stock);
        var choices = (FlexibleInputChoices) graph.factory(Map.of(0, 0, 1, 1), 0).get();
        for (int slot = 0; slot < 2; slot++) {
            int used = 0;
            for (var candidate : choices.preferredInputs(slot)) { choices.consume(slot, candidate.what(), 1); used++; }
            assertEquals(500, used);
        }
        assertTrue(choices.candidateVisits() <= 1_500);
        graph.setStock(seed, 0);
        assertEquals(999, graph.buckets.liveKeys.get(seed).size());
        graph.setStock(seed, 2);
        assertEquals(1_000, graph.buckets.liveKeys.get(seed).size());
        assertFeasible(graph);
    }

    @Test
    void fluidFragmentsAreAbsentFromPreferredKeysUntilOneWholeTemplateArrives() {
        var graph = new FlexibleInputChoices.Graph(List.of(new FlexibleInputChoices.Demand(0,
                Map.of(A, 1_000L, B, 1_000L), Map.of(A, 1_000L))), Map.of(A, 500L, B, 1_000L));
        var choices = graph.factory(Map.of(0, 0), 0).get();
        assertEquals(B, choices.preferredInputs(0).iterator().next().what());
        graph.setStock(A, 1_000);
        assertEquals(2, graph.buckets.liveKeys.values().iterator().next().size());
        graph.setStock(B, 999);
        var fresh = graph.factory(Map.of(0, 0), 0).get();
        assertEquals(A, fresh.preferredInputs(0).iterator().next().what());
        assertFeasible(graph);
    }

    private static FlexibleInputChoices.Graph crossing(long a, long b, long c) {
        return new FlexibleInputChoices.Graph(List.of(
                new FlexibleInputChoices.Demand(0, ordered(A, B), Map.of(A, a)),
                new FlexibleInputChoices.Demand(1, ordered(B, C), Map.of(B, b))), Map.of(A, a, B, b, C, c));
    }
    private static Map<AEKey, Long> ordered(AEKey... keys) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var key : keys) result.put(key, 1L);
        return result;
    }
    private static void assertFeasible(FlexibleInputChoices.Graph graph) {
        var totals = new java.util.HashMap<AEKey, Long>();
        for (int row = 0; row < graph.assigned.size(); row++) {
            assertEquals(graph.required[row], graph.assigned.get(row).values().stream().mapToLong(Long::longValue).sum());
            graph.assigned.get(row).forEach((key, amount) -> { assertTrue(amount > 0); totals.merge(key, amount, Long::sum); });
        }
        graph.stock.forEach((key, stock) -> {
            assertEquals(totals.getOrDefault(key, 0L), graph.totals.getOrDefault(key, 0L));
            assertTrue(totals.getOrDefault(key, 0L) <= stock);
        });
        assertTrue(graph.deficits.isEmpty());
    }
    private static AEKey key(String name) { return new ExecutionInputAllocatorTest.TestKey(name); }
    private static IPatternDetails pattern(Map<AEKey, Long> options, AtomicInteger checks) {
        var input = new IPatternDetails.IInput() {
            @Override public GenericStack[] getPossibleInputs() {
                return options.entrySet().stream().map(e -> new GenericStack(e.getKey(), e.getValue())).toArray(GenericStack[]::new);
            }
            @Override public long getMultiplier() { return 1; }
            @Override public boolean isValid(AEKey key, Level level) { checks.incrementAndGet(); return options.containsKey(key); }
            @Override public AEKey getRemainingKey(AEKey key) { return null; }
        };
        return new IPatternDetails() {
            @Override public AEItemKey getDefinition() { return null; }
            @Override public IInput[] getInputs() { return new IInput[] {input}; }
            @Override public GenericStack[] getOutputs() { return new GenericStack[]{new GenericStack(key("output"), 1)}; }
        };
    }
    private static final class CountingInventory implements ICraftingInventory {
        final ListCraftingInventory delegate = new ListCraftingInventory(key -> { });
        int fuzzyQueries;
        @Override public void insert(AEKey key, long amount, Actionable mode) { delegate.insert(key, amount, mode); }
        @Override public long extract(AEKey key, long amount, Actionable mode) { return delegate.extract(key, amount, mode); }
        @Override public Iterable<AEKey> findFuzzyTemplates(AEKey key) { fuzzyQueries++; return delegate.findFuzzyTemplates(key); }
    }
}
