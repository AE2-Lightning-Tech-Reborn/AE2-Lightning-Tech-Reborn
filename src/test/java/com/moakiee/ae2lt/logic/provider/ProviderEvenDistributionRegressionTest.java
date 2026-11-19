package com.moakiee.ae2lt.logic.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Arrays;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessDispatchMode;

class ProviderEvenDistributionRegressionTest {
    @Test
    void smallerRequestsUseWindowCreditAndRotateWithoutRestarting() {
        var dispatch = new ProviderWirelessDispatch();
        var pattern = new Pattern();
        var targets = targets(2);
        long[] window = new long[2];
        long[] initial = new long[2];
        assertEquals(0, batch(dispatch, pattern, targets, 0, 200, false, initial, window));
        assertArrayEquals(new long[] {100, 100}, initial);
        long[] first = new long[2];
        assertEquals(0, batch(dispatch, pattern, targets, 1, 10, false, first, window));
        assertArrayEquals(new long[] {10, 0}, first, "bounded skew reduces physical target visits");
        long[] second = new long[2];
        assertEquals(0, batch(dispatch, pattern, targets, 2, 10, false, second, window));
        assertArrayEquals(new long[] {0, 10}, second);
        assertArrayEquals(new long[] {110, 110}, window);
    }

    @Test
    void expiredWindowSpreadsAgainWhileKeepingPhysicalBatchHistory() {
        var dispatch = new ProviderWirelessDispatch();
        var pattern = new Pattern();
        var targets = targets(8);
        assertEquals(0, batch(dispatch, pattern, targets, 0, 800, false,
                new long[8], new long[8]));
        long[] received = new long[8];
        assertEquals(0, batch(dispatch, pattern, targets, 100, 80, false, received, new long[8]));
        for (long count : received) assertEquals(10, count);
    }

    @Test
    void batchAllocationsRespectTheRollingWindowAcrossExpiryAndRotatingVisits() {
        for (boolean fast : new boolean[] {false, true}) {
            for (int machines : new int[] {2, 3, 8, 32}) {
                var dispatch = new ProviderWirelessDispatch();
                var pattern = new Pattern();
                var targets = targets(machines);
                long[][] history = new long[100][machines];
                long[] window = new long[machines];
                for (int tick = 0; tick < 400; tick++) {
                    long[] row = history[tick % 100];
                    for (int i = 0; i < machines; i++) window[i] -= row[i];
                    Arrays.fill(row, 0);
                    long requested = machines * (1L + (tick * 37L) % 100);
                    long previousMax = Arrays.stream(window).max().orElseThrow();
                    batch(dispatch, pattern, targets, tick, requested, fast, row, window);
                    long min = Arrays.stream(window).min().orElseThrow();
                    long max = Arrays.stream(window).max().orElseThrow();
                    // Expiry can remove old work from a waiting machine before its
                    // next refill. New allocations must never enlarge that imbalance.
                    assertTrue(max <= Math.max(previousMax, 2 * Math.max(1, min)),
                            "machines=" + machines + " tick=" + tick + " window=" + Arrays.toString(window));
                }
            }
        }
    }

    private static long batch(ProviderWirelessDispatch dispatch, IPatternDetails pattern,
            List<WirelessConnection> targets, long tick, long copies, boolean fast, long[] received, long[] window) {
        dispatch.prepare(targets, tick, fast);
        boolean balancedWindow = Arrays.stream(window).allMatch(count -> count == window[0]);
        long remaining = dispatch.dispatchBatch(WirelessDispatchMode.EVEN_DISTRIBUTION,
                pattern, copies, tick, fast, (target, allowance, exploratory, preserve) -> {
                    int index = target.pos().getX();
                    long minimum = Arrays.stream(window).min().orElseThrow();
                    long prior = window[index];
                    var step = target.pushPatternStep(pattern, allowance, tick, true, preserve,
                            () -> false, count -> {
                                received[target.pos().getX()] += count;
                                return new ProviderTarget.BatchChunk(count, true, false);
                            });
                    if (!balancedWindow && targets.size() > 1) {
                        assertTrue(prior + step.ownedCopies() <= 2 * Math.max(1, minimum),
                                "allocation exceeded window ratio at tick=" + tick + " target=" + index);
                    }
                    window[index] += step.ownedCopies();
                    return new ProviderWirelessDispatch.BatchAttemptResult(step.ownedCopies(),
                            step.attemptedCopies(), step.acceptedFullChunk(), step.requestLimited(),
                            step.baselineStatus(), step.ownedCopies() > 0
                                    ? WirelessPushOutcome.SUCCESS : WirelessPushOutcome.SOFT_FAIL);
                }, ignored -> true, ignored -> fail("live target removed"));
        assertEquals(copies, Arrays.stream(received).sum() + remaining, "physical ownership conservation");
        return remaining;
    }

    @Test
    void singleCopyBalancesEachInterleavedPattern() {
        var dispatch = new ProviderWirelessDispatch();
        var patterns = List.of(new Pattern(), new Pattern());
        var targets = targets(2);
        long[][] received = new long[2][2];
        for (int tick = 0; tick < 100; tick++) {
            dispatch.prepare(targets, tick, false);
            for (int recipe = 0; recipe < 2; recipe++) {
                int index = recipe;
                assertTrue(single(dispatch, patterns.get(recipe), tick, target -> {
                    received[index][target.pos().getX()]++;
                    return WirelessPushOutcome.SUCCESS;
                }));
            }
        }
        assertArrayEquals(new long[] {50, 50}, received[0]);
        assertArrayEquals(new long[] {50, 50}, received[1]);
    }

    @Test
    void rejectedSingleCopyTargetsCannotKeepWinningTheAttemptBudget() {
        for (var rejection : List.of(WirelessPushOutcome.SOFT_FAIL, WirelessPushOutcome.HARD_FAIL)) {
            var dispatch = new ProviderWirelessDispatch();
            var pattern = new Pattern();
            dispatch.prepare(targets(3), 0, false);
            int[] calls = new int[3];
            ProviderWirelessDispatch.SingleAttempt attempt = target -> {
                int index = target.pos().getX();
                calls[index]++;
                return index < 2 ? rejection : WirelessPushOutcome.SUCCESS;
            };
            assertFalse(single(dispatch, pattern, 0, attempt));
            assertTrue(single(dispatch, pattern, 0, attempt));
            assertTrue(single(dispatch, pattern, 0, attempt));
            assertArrayEquals(new int[] {1, 1, 2}, calls);
        }
    }

    @Test
    void globalAbortDoesNotConsumeARecipesFairTurn() {
        var dispatch = new ProviderWirelessDispatch();
        var pattern = new Pattern();
        var targets = targets(2);
        dispatch.prepare(targets, 0, false);
        var visited = new java.util.ArrayList<WirelessConnection>();
        assertFalse(single(dispatch, pattern, 0, target -> {
            visited.add(target);
            return WirelessPushOutcome.GLOBAL_ABORT;
        }));
        try (var pass = dispatch.beginFairPass(pattern, 0)) {
            for (var target : targets) assertEquals(1, pass.allowance(target), "abort must not own a copy");
        }
        for (int call = 0; call < 2; call++) {
            assertTrue(single(dispatch, pattern, 0, target -> {
                visited.add(target);
                return WirelessPushOutcome.SUCCESS;
            }));
        }
        assertEquals(targets.get(0), visited.getFirst());
        assertEquals(new java.util.HashSet<>(targets), new java.util.HashSet<>(visited.subList(1, 3)));
    }

    @Test
    void singleCopyOverflowPausesAndResumesTheTargetWithoutDoubleCounting() {
        var dispatch = new ProviderWirelessDispatch();
        var pattern = new Pattern();
        var targets = targets(2);
        dispatch.prepare(targets, 0, false);
        long[] received = new long[2];
        assertTrue(single(dispatch, pattern, 0, target -> {
            assertEquals(targets.get(0), target);
            received[0]++;
            dispatch.overflow().restoreBucket(target,
                    WirelessOverflowQueue.Bucket.fallback((short) 0, List.of()), 0);
            return WirelessPushOutcome.SUCCESS;
        }));
        assertTrue(single(dispatch, pattern, 0, target -> {
            assertEquals(targets.get(1), target);
            received[1]++;
            return WirelessPushOutcome.SUCCESS;
        }));
        dispatch.overflow().remove(targets.get(0));
        dispatch.resumeTarget(targets.get(0), 0);
        assertTrue(single(dispatch, pattern, 0, target -> {
            assertEquals(targets.get(0), target);
            received[0]++;
            return WirelessPushOutcome.SUCCESS;
        }));
        assertArrayEquals(new long[] {2, 1}, received);
    }

    private static boolean single(ProviderWirelessDispatch dispatch, IPatternDetails pattern,
            long tick, ProviderWirelessDispatch.SingleAttempt attempt) {
        return dispatch.dispatchSingleCopy(WirelessDispatchMode.EVEN_DISTRIBUTION,
                pattern, tick, false, 2, attempt, ignored -> true, ignored -> fail("live target removed"));
    }

    private static List<WirelessConnection> targets(int count) {
        return IntStream.range(0, count).mapToObj(index -> new WirelessConnection(
                Level.OVERWORLD, new BlockPos(index, 64, 0), Direction.NORTH)).toList();
    }

    private static final class Pattern implements IPatternDetails {
        public AEItemKey getDefinition() { return null; }
        public IInput[] getInputs() { return new IInput[0]; }
        public List<GenericStack> getOutputs() { return List.of(); }
        public int hashCode() { throw new AssertionError("unexpected pattern hash"); }
        public boolean equals(Object other) { throw new AssertionError("unexpected pattern equality"); }
    }
}
