package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;

class Pr12ReviewFixRegressionTest {
    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void controlSegmentedRefillSucceedsWithinPerCallLimit() {
        var fixture = learnSegmentedReservoir();
        fixture.machine.stored = 0;
        fixture.machine.attempted.clear();
        var result = fixture.target.pushPatternStep(fixture.pattern, 10000, 31, true, true,
                () -> false, fixture.machine::push);
        assertEquals(2000, result.ownedCopies());
        assertTrue(fixture.machine.attempted.stream().allMatch(copies -> copies <= 1024));
    }

    @Test
    void segmentedProofMustNotBecomeAnUnprovenAtomicRequest() {
        var fixture = learnSegmentedReservoir();
        fixture.machine.stored = 0;
        fixture.machine.attempted.clear();
        fixture.target.preferSegmentedReservoirRefill(fixture.pattern, true);
        var result = fixture.target.pushPatternStep(fixture.pattern, 10000, 31, true, true,
                () -> false, fixture.machine::push);
        assertTrue(fixture.machine.attempted.stream().allMatch(copies -> copies <= 1024),
                "Segmented proof became atomic requests above actual per-call limit: " + fixture.machine.attempted);
        assertEquals(2000, result.ownedCopies());
        assertEquals(List.of(512, 512, 976), fixture.machine.attempted);
    }

    @Test
    void suppressedTailIsReplayedOnlyForProvenTimedRefill() {
        var fixture = learnSegmentedReservoir();
        fixture.machine.stored = 0;
        var probeCalls = new ArrayList<Integer>();
        var early = fixture.target.pushPatternStep(fixture.pattern, 10000, 31, true, true,
                () -> false, copies -> {
                    probeCalls.add(copies);
                    return probeCalls.size() == 3 ? ProviderTarget.BatchChunk.REJECTED
                            : new ProviderTarget.BatchChunk(copies, true, false);
                });
        assertEquals(1024, early.ownedCopies());
        assertEquals(ProviderTarget.BaselineStatus.RESERVOIR_PREFIX_COMPLETE, early.baselineStatus());
        fixture.machine.attempted.clear();
        fixture.target.preferSegmentedReservoirRefill(fixture.pattern, true);
        var refill = fixture.target.pushPatternStep(fixture.pattern, 10000, 50, true, true,
                () -> false, fixture.machine::push);
        assertEquals(List.of(512, 512, 976), fixture.machine.attempted);
        assertEquals(2000, refill.ownedCopies());
    }

    @Test
    void controlCallerLimitedSuccessUsesOrdinaryCadenceWhenNotBulk() {
        var pattern = new ProviderSchedulerRetentionTest.Pattern();
        var cadence = new WirelessBatchCadence<String>();
        cadence.recordSuccess("target", pattern, 0, 2000, false);
        int delay = cadence.recordSuccess("target", pattern, 10, 512, true);
        assertTrue(delay <= 5);
    }

    @Test
    void callerLimitedSuccessMustNotRetainFullBulkWait() {
        var pattern = new ProviderSchedulerRetentionTest.Pattern();
        var cadence = learnedCadence(pattern);
        int delay = cadence.recordSuccess("target", pattern, 25, 512, true);
        assertTrue(delay <= 5, "512-copy caller-limited success still waits full bulk period: " + delay);
        assertFalse(cadence.usesBulkRefill("target", pattern, 2000));
        for (int tick = 30; tick <= 60; tick += 5) {
            assertTrue(cadence.recordSuccess("target", pattern, tick, 512, true) <= 5);
            assertFalse(cadence.usesBulkRefill("target", pattern, 2000));
        }
    }

    @Test
    void controlColdPatternIsExpiredWithoutCatalogReset() throws Exception {
        var dispatch = new ProviderWirelessDispatch();
        var targets = ProviderSchedulerRetentionTest.targets(8);
        var cold = new ProviderSchedulerRetentionTest.Pattern();
        var hot = new ProviderSchedulerRetentionTest.Pattern();
        ProviderSchedulerRetentionTest.dispatchTick(dispatch, targets, cold, 0);
        for (int tick = 1; tick <= 240; tick++) {
            ProviderSchedulerRetentionTest.dispatchTick(dispatch, targets, hot, tick);
        }
        assertEquals(8, ProviderSchedulerRetentionTest.retained(dispatch, targets).physicalPairs());
    }

    @Test
    void catalogChangesMustKeepColdHistoryIndexedForExpiry() throws Exception {
        var dispatch = new ProviderWirelessDispatch();
        var targets = ProviderSchedulerRetentionTest.targets(8);
        for (int tick = 0; tick < 20; tick++) {
            ProviderSchedulerRetentionTest.dispatchTick(dispatch, targets,
                    new ProviderSchedulerRetentionTest.Pattern(), tick);
            dispatch.patternsChanged();
        }
        var hot = new ProviderSchedulerRetentionTest.Pattern();
        for (int tick = 20; tick <= 240; tick++) {
            ProviderSchedulerRetentionTest.dispatchTick(dispatch, targets, hot, tick);
        }
        var retained = ProviderSchedulerRetentionTest.retained(dispatch, targets);
        assertEquals(8, retained.physicalPairs(),
                "Only eight hot target histories should survive TTL, not removed patterns");
    }

    @Test
    void preferredRefillNeverExceedsCallerAllowance() {
        var fixture = learnSegmentedReservoir();
        fixture.machine.stored = 0;
        fixture.machine.attempted.clear();
        fixture.target.preferSegmentedReservoirRefill(fixture.pattern, true);
        var result = fixture.target.pushPatternStep(fixture.pattern, 1536, 31, true, true,
                () -> false, fixture.machine::push);
        assertEquals(List.of(512, 512, 512), fixture.machine.attempted);
        assertEquals(1536, result.ownedCopies());
        assertTrue(result.requestLimited());
    }

    @ParameterizedTest
    @ValueSource(strings = {"rejected", "partial", "abort"})
    void segmentedRefillStopsAtFailedSegmentAndKeepsPriorOwnership(String failure) {
        var fixture = learnSegmentedReservoir();
        var calls = new ArrayList<Integer>();
        fixture.target.preferSegmentedReservoirRefill(fixture.pattern, true);
        var result = fixture.target.pushPatternStep(fixture.pattern, 10000, 31, true, true,
                () -> false, copies -> {
                    calls.add(copies);
                    if (calls.size() == 1) return new ProviderTarget.BatchChunk(copies, true, false);
                    return switch (failure) {
                        case "partial" -> new ProviderTarget.BatchChunk(100, false, false);
                        case "abort" -> ProviderTarget.BatchChunk.GLOBAL_ABORT;
                        default -> ProviderTarget.BatchChunk.REJECTED;
                    };
                });
        assertEquals(List.of(512, 512), calls);
        assertEquals(failure.equals("partial") ? 612 : 512, result.ownedCopies());
        assertFalse(result.acceptedFullChunk());
        assertEquals(failure.equals("abort"), result.globalAbort());
    }

    @Test
    void catalogChangesDoNotRestartIdleExpiryOrLoseMaintenanceWork() throws Exception {
        var wakeups = new AtomicInteger();
        var saves = new AtomicInteger();
        var dispatch = new ProviderWirelessDispatch(wakeups::incrementAndGet, saves::incrementAndGet);
        var targets = ProviderSchedulerRetentionTest.targets(8);
        for (int patternIndex = 0; patternIndex < 20; patternIndex++) {
            ProviderSchedulerRetentionTest.dispatchTick(dispatch, targets,
                    new ProviderSchedulerRetentionTest.Pattern(), 0);
        }
        assertEquals(1, wakeups.get());
        for (int tick = 1; tick <= 100; tick++) {
            dispatch.patternsChanged();
            assertTrue(dispatch.hasMaintenanceWork());
            dispatch.maintain(tick);
        }
        assertEquals(160, ProviderSchedulerRetentionTest.retained(dispatch, targets).physicalPairs());
        dispatch.maintain(101);
        assertEquals(128, ProviderSchedulerRetentionTest.retained(dispatch, targets).physicalPairs());
        dispatch.maintain(101);
        assertEquals(128, ProviderSchedulerRetentionTest.retained(dispatch, targets).physicalPairs());
        for (int tick = 102; tick <= 105; tick++) dispatch.maintain(tick);
        assertEquals(0, ProviderSchedulerRetentionTest.retained(dispatch, targets).physicalPairs());
        assertFalse(dispatch.hasMaintenanceWork());
        assertEquals(5, saves.get());
    }

    private static Learned learnSegmentedReservoir() {
        var target = new ProviderTarget(Level.OVERWORLD, BlockPos.ZERO, Direction.NORTH);
        var pattern = new ProviderSchedulerRetentionTest.Pattern();
        var machine = new Machine();
        target.pushPatternStep(pattern, 1024, 0, true, () -> false, machine::push);
        for (int tick = 1; tick <= 30; tick++) {
            machine.stored = 0;
            target.pushPatternStep(pattern, 10000, tick, true, false, () -> false, machine::push);
        }
        assertEquals(2000, machine.stored, "fixture must actually learn full segmented capacity");
        assertTrue(machine.accepted.stream().allMatch(copies -> copies <= 1024));
        assertEquals(2000, target.reservoirRefillCapacity(pattern, 31));
        return new Learned(target, pattern, machine);
    }

    private static WirelessBatchCadence<String> learnedCadence(IPatternDetails pattern) {
        var cadence = new WirelessBatchCadence<String>();
        cadence.recordSuccess("target", pattern, 0, 2000, false);
        assertFalse(cadence.usesBulkRefill("target", pattern, 2000));
        for (int tick = 5; tick <= 20; tick += 5) {
            cadence.recordSuccess("target", pattern, tick, 512, false,
                    ProviderTarget.BaselineStatus.PREFIX_COMPLETE);
        }
        assertTrue(cadence.usesBulkRefill("target", pattern, 2000));
        return cadence;
    }

    private static final class Machine {
        int stored;
        final List<Integer> attempted = new ArrayList<>();
        final List<Integer> accepted = new ArrayList<>();

        ProviderTarget.BatchChunk push(int copies) {
            attempted.add(copies);
            if (copies > 1024 || stored + copies > 2000) return ProviderTarget.BatchChunk.REJECTED;
            stored += copies;
            accepted.add(copies);
            return new ProviderTarget.BatchChunk(copies, true, false);
        }
    }

    private record Learned(ProviderTarget target, IPatternDetails pattern, Machine machine) {}
}
