package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.me.key.LightningKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdaptiveLoopSeedLoansTest {
    private static final AEKey A = LightningKey.HIGH_VOLTAGE;
    private static final AEKey B = LightningKey.EXTREME_HIGH_VOLTAGE;

    @Test
    void thirtyTwoRequestedWithOnlyTheMinimumAlreadyBorrowedStartsAtOne() {
        var request = request(true, Map.of(A, 8L));
        var stock = new Stock(Map.of());
        var result = AdaptiveLoopSeedLoans.borrow(List.of(request), key -> 8L, stock);
        assertEquals(1, result.multipliers().get(request.group()));
        assertTrue(result.borrowed().isEmpty());
    }

    @Test
    void usesAvailableWholeSeedSetsUpToTheConfiguredLimit() {
        for (long spare : new long[] {4, 16, 248, 1000}) {
            var request = request(true, Map.of(A, 8L));
            var stock = new Stock(Map.of(A, spare));
            var result = AdaptiveLoopSeedLoans.borrow(List.of(request), key -> 8L, stock);
            long expectedMultiplier = Math.min(32, 1 + spare / 8);
            assertEquals(expectedMultiplier, result.multipliers().get(request.group()).longValue());
            assertEquals((expectedMultiplier - 1) * 8, result.borrowed().get(A));
            assertEquals(spare - result.borrowed().get(A), stock.amount(A));
        }
    }

    @Test
    void multipleSeedTypesUseTheSmallestCompleteMultiplier() {
        var request = request(false, Map.of(A, 8L, B, 2L));
        var stock = new Stock(Map.of(A, 248L, B, 2L));
        var result = AdaptiveLoopSeedLoans.borrow(List.of(request), key -> 0L, stock);
        assertEquals(2, result.multipliers().get(request.group()));
        assertEquals(8, result.borrowed().get(A));
        assertEquals(2, result.borrowed().get(B));
        assertEquals(240, stock.amount(A));
    }

    @Test
    void optionalStockDisappearingAfterSimulationFallsBackWithoutCreatingSeedDebt() {
        var request = request(false, Map.of(A, 8L, B, 2L));
        var stock = new Stock(Map.of(A, 248L, B, 62L));
        stock.unavailableOnCommit = B;
        var result = AdaptiveLoopSeedLoans.borrow(List.of(request), key -> 0L, stock);
        assertEquals(1, result.multipliers().get(request.group()));
        assertTrue(result.borrowed().isEmpty());
        assertEquals(248, stock.amount(A), "the incomplete A top-up is returned");
    }

    @Test
    void partiallyChangedHostStillCommitsOnlyTheCompleteSets() {
        var request = request(false, Map.of(A, 8L, B, 2L));
        var stock = new Stock(Map.of(A, 248L, B, 62L));
        stock.commitLimit = 5;
        var result = AdaptiveLoopSeedLoans.borrow(List.of(request), key -> 0L, stock);
        assertEquals(1, result.multipliers().get(request.group()));
        assertTrue(result.borrowed().isEmpty());
        assertEquals(248, stock.amount(A));
        assertEquals(62, stock.amount(B));
    }

    @Test
    void sharedLoopsBorrowPhysicalSeedsOnlyOnce() {
        var first = request(true, Map.of(A, 8L));
        var second = request(true, Map.of(A, 8L));
        var stock = new Stock(Map.of(A, 248L));
        var result = AdaptiveLoopSeedLoans.borrow(List.of(first, second), key -> 8L, stock);
        assertEquals(Map.of(first.group(), 32, second.group(), 32), result.multipliers());
        assertEquals(248, result.borrowed().get(A));
        assertEquals(0, stock.amount(A));
    }

    @Test
    void dedicatedLoopsCannotBorrowAnotherLoopsExtraSeeds() {
        var first = request(false, Map.of(A, 8L));
        var second = request(false, Map.of(A, 8L));
        var stock = new Stock(Map.of(A, 248L));
        var result = AdaptiveLoopSeedLoans.borrow(List.of(first, second), key -> 0L, stock);
        assertEquals(Map.of(first.group(), 32, second.group(), 1), result.multipliers());
        assertEquals(248, result.borrowed().get(A));
    }

    @Test
    void existingLargerSharedPoolNeedsNoAdditionalPhysicalLoan() {
        var request = request(true, Map.of(A, 8L));
        var result = AdaptiveLoopSeedLoans.borrow(List.of(request), key -> 32L, new Stock(Map.of()));
        assertEquals(4, result.multipliers().get(request.group()));
        assertTrue(result.borrowed().isEmpty());
    }

    private static AdaptiveLoopSeedLoans.Request request(boolean shared, Map<AEKey, Long> unit) {
        return new AdaptiveLoopSeedLoans.Request(UUID.randomUUID(), shared, unit, 32);
    }

    private static final class Stock implements AdaptiveLoopSeedLoans.Stock {
        private final Map<AEKey, Long> items;
        private AEKey unavailableOnCommit;
        private long commitLimit = Long.MAX_VALUE;

        Stock(Map<AEKey, Long> items) { this.items = new HashMap<>(items); }
        long amount(AEKey key) { return items.getOrDefault(key, 0L); }

        @Override public long extract(AEKey key, long amount, Actionable mode) {
            if (mode == Actionable.MODULATE && key.equals(unavailableOnCommit)) return 0;
            long taken = Math.min(amount, amount(key));
            if (mode == Actionable.MODULATE) {
                taken = Math.min(taken, commitLimit);
                items.put(key, amount(key) - taken);
            }
            return taken;
        }

        @Override public void refund(AEKey key, long amount) { items.merge(key, amount, Long::sum); }
    }
}
