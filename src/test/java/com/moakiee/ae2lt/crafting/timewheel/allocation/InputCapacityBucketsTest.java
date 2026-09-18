package com.moakiee.ae2lt.crafting.timewheel.allocation;

import com.moakiee.thunderbolt.core.crafting.batch.SharedBatchInputPattern;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.crafting.timewheel.allocation.CraftingInputAllocation.Choices;

class InputCapacityBucketsTest {
    @Test
    void tenThousandKeysWithTwoConsumersUseOneBucketAndNoResidualSearch() {
        var options = new LinkedHashMap<AEKey, Long>();
        var stock = new LinkedHashMap<AEKey, Long>();
        var left = new LinkedHashMap<AEKey, Long>();
        var right = new LinkedHashMap<AEKey, Long>();
        for (int i = 0; i < 10_000; i++) {
            AEKey key = new ExecutionInputAllocatorTest.TestKey("bucket_key_" + i);
            options.put(key, 1L);
            stock.put(key, 1L);
            (i < 5_000 ? left : right).put(key, 1L);
        }
        var choices = (FlexibleInputChoices) FlexibleInputChoices.factory(List.of(
                new FlexibleInputChoices.Demand(0, options, left),
                new FlexibleInputChoices.Demand(1, options, right)), stock, 0).get();
        assertEquals(1, choices.bucketCount());
        for (var key : right.keySet()) {
            assertEquals(1, choices.available(0, key, 1), "bucket peers are interchangeable without a search");
            choices.consume(0, key, 1);
            assertEquals(0, choices.available(1, key, 1), "the other row cannot spend the same physical key");
        }
        assertEquals(0, choices.remainingWork());
        assertEquals(1, choices.touchedRows());
        assertEquals(1, choices.available(1, left.keySet().iterator().next(), 1));
    }

    @Test
    void exactConsumerAndDifferentUnitSizesParticipateInBucketSignatures() {
        AEKey a = new ExecutionInputAllocatorTest.TestKey("a");
        AEKey b = new ExecutionInputAllocatorTest.TestKey("b");
        var buckets = new InputCapacityBuckets(List.of(Map.of(a, 1L, b, 1L), Map.of(a, 1L)), Map.of(a, 2L, b, 2L));
        assertEquals(2, buckets.capacity.size());
        var units = new InputCapacityBuckets(List.of(Map.of(a, 1L, b, 2L)), Map.of(a, 2L, b, 2L));
        assertEquals(2, units.capacity.size());
    }

    @Test
    void subTemplateFragmentsAreNotPooledIntoACompleteInput() {
        AEKey a = new ExecutionInputAllocatorTest.TestKey("fluid_a");
        AEKey b = new ExecutionInputAllocatorTest.TestKey("fluid_b");
        var buckets = new InputCapacityBuckets(List.of(Map.of(a, 1_000L, b, 1_000L)), Map.of(a, 500L, b, 500L));
        assertEquals(1, buckets.capacity.size());
        assertEquals(0, buckets.capacity.values().iterator().next());
    }

    @Test
    void batchLowerBoundChecksSharedBucketAcrossDifferentConcreteKeys() throws Exception {
        AEKey a = new ExecutionInputAllocatorTest.TestKey("a");
        AEKey b = new ExecutionInputAllocatorTest.TestKey("b");
        AEKey c = new ExecutionInputAllocatorTest.TestKey("c");
        var choices = FlexibleInputChoices.factory(List.of(
                new FlexibleInputChoices.Demand(0, Map.of(a, 1L, b, 1L, c, 1L), Map.of(a, 2L, c, 2L)),
                new FlexibleInputChoices.Demand(-1, Map.of(a, 1L, b, 1L), Map.of(b, 2L))),
                Map.of(a, 2L, b, 2L, c, 2L), 32_768).get();
        var input = new KeyCounter();
        input.add(a, 1);
        input.add(b, 1);
        assertEquals(2, choices.assigned(0, a));
        assertEquals(2, choices.assigned(0, b));
        var method = TimeWheelInputExtractor.class.getDeclaredMethod(
                "flexibleAdditionalCopies", Choices.class, KeyCounter[].class, long.class);
        method.setAccessible(true);
        var allowance = (TimeWheelInputExtractor.BatchAllowance) method.invoke(null, choices, new KeyCounter[] {input}, 2L);
        assertEquals(1L, allowance.copies());
        assertEquals(2, choices.assigned(0, a), "joint checks roll back their quantities");
    }

    @Test
    void sparseMatchingResumesInsteadOfRestartingAndDoesNotUseARowKeyMatrix() {
        var requests = new java.util.ArrayList<BucketAllocationSearch.Request>();
        var stock = new LinkedHashMap<AEKey, Long>();
        AEKey[] keys = new AEKey[501];
        for (int i = 0; i < keys.length; i++) { keys[i] = new ExecutionInputAllocatorTest.TestKey("chain_" + i); stock.put(keys[i], 1L); }
        for (int i = 0; i < 500; i++) requests.add(new BucketAllocationSearch.Request(
                Map.of(keys[i], 1L, keys[i + 1], 1L), 1, 1, false));
        var search = new BucketAllocationSearch(requests, stock, 1, 1, false);
        int slices = 0;
        while (!search.advance(113)) assertTrue(++slices < 20_000, "saved BFS/path progress must eventually finish");
        assertTrue(slices > 1);
        var result = search.result();
        assertNotNull(result);
        var used = new java.util.HashMap<AEKey, Long>();
        for (int i = 0; i < 500; i++) {
            assertEquals(1L, result.get(i).values().stream().mapToLong(Long::longValue).sum());
            result.get(i).forEach((key, amount) -> { assertTrue(stock.containsKey(key)); used.merge(key, amount, Long::sum); });
        }
        assertTrue(used.values().stream().allMatch(amount -> amount == 1));
    }

    @Test
    void renewableBatchSearchRetainsItsBinarySearchAcrossSlices() {
        AEKey a = new ExecutionInputAllocatorTest.TestKey("a");
        AEKey b = new ExecutionInputAllocatorTest.TestKey("b");
        var search = new BucketAllocationSearch(List.of(
                new BucketAllocationSearch.Request(Map.of(a, 1L, b, 1L), 100, 1, true),
                new BucketAllocationSearch.Request(Map.of(a, 1L), 3, 1, false)),
                Map.of(a, 3L, b, 7L), 1, 100, true);
        int slices = 0;
        while (!search.advance(7)) assertTrue(++slices < 1_000);
        assertNotNull(search.result());
        assertEquals(7L, search.result().get(0).values().stream().mapToLong(Long::longValue).sum());
        assertEquals(Map.of(a, 3L), search.result().get(1));
    }
}
