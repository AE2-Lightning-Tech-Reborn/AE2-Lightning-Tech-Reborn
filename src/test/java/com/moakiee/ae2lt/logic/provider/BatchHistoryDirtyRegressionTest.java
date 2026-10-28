package com.moakiee.ae2lt.logic.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

class BatchHistoryDirtyRegressionTest {
    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private final ProviderTarget target = new ProviderTarget(
            Level.OVERWORLD, BlockPos.ZERO, Direction.NORTH);
    private final ProviderSchedulerRetentionTest.Pattern pattern =
            new ProviderSchedulerRetentionTest.Pattern();

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void throwingInsertionStillMarksChangedHistoryDirty(boolean sameTick) {
        target.pushPatternStep(pattern, 4, 0, true, () -> false,
                copies -> new ProviderTarget.BatchChunk(copies, true, false));
        target.consumeAdaptiveBatchHistoryDirty();
        var before = target.adaptiveBatchSnapshots().get(pattern);
        var calls = new AtomicInteger();
        var failure = new IllegalStateException("test insertion failure");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> target.pushPatternStep(pattern, 100, sameTick ? 0 : 1, true,
                        () -> false, copies -> {
                            if (calls.incrementAndGet() == 4) throw failure;
                            return new ProviderTarget.BatchChunk(copies, true, false);
                        })));
        assertNotEquals(before, target.adaptiveBatchSnapshots().get(pattern));
        assertTrue(target.consumeAdaptiveBatchHistoryDirty(),
                "An exceptional exit must not hide persisted state mutations");
        assertFalse(target.consumeAdaptiveBatchHistoryDirty());
    }

    @Test
    void throwingMidRampBlockCheckStillMarksHistoryDirty() {
        var checks = new AtomicInteger();
        assertThrows(IllegalStateException.class,
                () -> target.pushPatternStep(pattern, 100, 0, true,
                        () -> {
                            if (checks.incrementAndGet() == 3)
                                throw new IllegalStateException("test block check failure");
                            return false;
                        }, copies -> new ProviderTarget.BatchChunk(copies, true, false)));
        assertTrue(target.consumeAdaptiveBatchHistoryDirty());
    }

    @Test
    void sameTickUnchangedRejectionAndTransientPreferenceStayClean() {
        target.pushPatternStep(pattern, 4, 0, true, () -> false,
                copies -> new ProviderTarget.BatchChunk(copies, true, false));
        target.consumeAdaptiveBatchHistoryDirty();
        var before = target.adaptiveBatchSnapshots().get(pattern);
        target.preferSegmentedReservoirRefill(pattern, true);
        target.pushPatternStep(pattern, 100, 0, true, true, () -> false,
                copies -> ProviderTarget.BatchChunk.REJECTED);
        assertEquals(before, target.adaptiveBatchSnapshots().get(pattern));
        assertFalse(target.consumeAdaptiveBatchHistoryDirty());
    }

    @Test
    void unchangedSameTickExceptionDoesNotRequestRedundantSave() {
        target.pushPatternStep(pattern, 4, 0, true, () -> false,
                copies -> new ProviderTarget.BatchChunk(copies, true, false));
        target.consumeAdaptiveBatchHistoryDirty();
        var before = target.adaptiveBatchSnapshots().get(pattern);
        assertThrows(IllegalStateException.class,
                () -> target.pushPatternStep(pattern, 100, 0, true, true,
                        () -> false, copies -> { throw new IllegalStateException("test"); }));
        assertEquals(before, target.adaptiveBatchSnapshots().get(pattern));
        assertFalse(target.consumeAdaptiveBatchHistoryDirty());
    }

    @Test
    void snapshotEqualityCoversEveryPersistedComponent() throws Exception {
        var type = Class.forName(ProviderTarget.class.getName() + "$BatchStepState");
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        var snapshot = type.getDeclaredMethod("snapshot");
        snapshot.setAccessible(true);
        for (var component : ProviderTarget.BatchStepSnapshot.class.getRecordComponents()) {
            var state = constructor.newInstance();
            var before = snapshot.invoke(state);
            assertEquals(before, snapshot.invoke(state));
            var field = type.getDeclaredField(component.getName());
            field.setAccessible(true);
            if (field.getType() == boolean.class) field.setBoolean(state, !field.getBoolean(state));
            else if (field.getType() == int.class) field.setInt(state, field.getInt(state) + 1);
            else field.setLong(state, field.getLong(state) + 1);
            assertNotEquals(before, snapshot.invoke(state), component.getName());
        }
    }

    @Test
    void dirtyTrackingMatchesPersistedSnapshotsAcrossMixedSteps() {
        var random = new Random(0xAE21020L);
        long tick = 0;
        for (int step = 0; step < 2000; step++) {
            int index = step;
            if (step % 97 == 0) tick = Math.max(0, tick - 5);
            else if (step % 41 == 0) tick += 101;
            else tick += step % 3 == 0 ? 0 : 1;
            var before = target.adaptiveBatchSnapshots().get(pattern);
            target.consumeAdaptiveBatchHistoryDirty();
            int[] free = {1 + random.nextInt(3000)};
            target.pushPatternStep(pattern, 1 + random.nextInt(4096), tick, true,
                    step % 2 == 0, () -> false, copies -> {
                        if (index % 11 == 0) return ProviderTarget.BatchChunk.GLOBAL_ABORT;
                        if (copies > free[0]) return ProviderTarget.BatchChunk.REJECTED;
                        free[0] -= copies;
                        return new ProviderTarget.BatchChunk(copies,
                                index % 13 != 0, false);
                    });
            var after = target.adaptiveBatchSnapshots().get(pattern);
            assertEquals(!java.util.Objects.equals(before, after),
                    target.consumeAdaptiveBatchHistoryDirty(), "step=" + step);
        }
    }

    @Test
    void reportSteadyStateAllocationWithoutChangingAcceptance() {
        var bean = java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(
                bean instanceof com.sun.management.ThreadMXBean);
        var allocation = (com.sun.management.ThreadMXBean) bean;
        org.junit.jupiter.api.Assumptions.assumeTrue(allocation.isThreadAllocatedMemorySupported());
        allocation.setThreadAllocatedMemoryEnabled(true);
        java.util.function.IntFunction<ProviderTarget.BatchChunk> reject =
                copies -> ProviderTarget.BatchChunk.REJECTED;
        java.util.function.BooleanSupplier unblocked = () -> false;
        // Warm up the same physical path, consuming dirty flags just as dispatch does.
        for (int tick = 0; tick < 100_000; tick++) {
            target.pushPatternStep(pattern, 100, tick, true, true, unblocked, reject);
            target.consumeAdaptiveBatchHistoryDirty();
        }
        long thread = Thread.currentThread().getId();
        long before = allocation.getThreadAllocatedBytes(thread);
        long owned = 0;
        for (int tick = 100_000; tick < 200_000; tick++) {
            owned += target.pushPatternStep(pattern, 100, tick,
                    true, true, unblocked, reject).ownedCopies();
            target.consumeAdaptiveBatchHistoryDirty();
        }
        long bytes = allocation.getThreadAllocatedBytes(thread) - before;
        assertEquals(0, owned);
        System.out.println("BATCH_HISTORY_ALLOCATION bytesPerStep=" + bytes / 100_000.0);
    }
}
