package com.moakiee.ae2lt.logic.provider;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import appeng.api.crafting.IPatternDetails;

/** Executable regression coverage of the three P2 review comments on PR #12. */
class Pr12ReviewFixRegressionTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    private static final class Machine {
        int stored;
        final List<Integer> attempted = new ArrayList<>();
        final List<Integer> accepted = new ArrayList<>();
        ProviderTarget.BatchChunk push(int copies) {
            attempted.add(copies);
            if (copies > 1024 || stored + copies > 2000) return ProviderTarget.BatchChunk.REJECTED;
            stored += copies; accepted.add(copies);
            return new ProviderTarget.BatchChunk(copies, true, false);
        }
    }
    private record Learned(ProviderTarget target, IPatternDetails pattern, Machine machine) {}
    private static Learned learnSegmentedReservoir() {
        var target = new ProviderTarget(Level.OVERWORLD, BlockPos.ZERO, Direction.NORTH);
        var pattern = new ProviderSchedulerRetentionTest.Pattern();
        var machine = new Machine();
        target.pushPatternStep(pattern, 1024, 0, true, () -> false, machine::push);
        for (int tick=1; tick<=30; tick++) {
            machine.stored=0;
            target.pushPatternStep(pattern, 10000, tick, true, false, () -> false, machine::push);
        }
        assertEquals(2000, machine.stored, "fixture must actually learn full segmented capacity");
        assertTrue(machine.accepted.stream().allMatch(n -> n <= 1024));
        assertEquals(2000, target.reservoirRefillCapacity(pattern, 31));
        return new Learned(target, pattern, machine);
    }
    @Test void controlSegmentedRefillSucceedsWithinPerCallLimit() {
        var f=learnSegmentedReservoir();f.machine.stored=0;f.machine.attempted.clear();
        var result=f.target.pushPatternStep(f.pattern, 10000, 31, true, true, () -> false, f.machine::push);
        System.out.println("CONTROL segmented attempts="+f.machine.attempted+" owned="+result.ownedCopies());
        assertEquals(2000, result.ownedCopies());
        assertTrue(f.machine.attempted.stream().allMatch(n -> n <= 1024));
    }
    @Test void segmentedProofMustNotBecomeAnUnprovenAtomicRequest() {
        var f=learnSegmentedReservoir();f.machine.stored=0;f.machine.attempted.clear();
        f.target.preferSegmentedReservoirRefill(f.pattern, true);
        var result=f.target.pushPatternStep(f.pattern, 10000, 31, true, true, () -> false, f.machine::push);
        System.out.println("segmented bulk attempts="+f.machine.attempted+" owned="+result.ownedCopies());
        assertTrue(f.machine.attempted.stream().allMatch(n -> n <= 1024),
                "Segmented proof became atomic requests above actual per-call limit: "+f.machine.attempted);
        assertEquals(2000, result.ownedCopies());
        assertEquals(List.of(512, 512, 976), f.machine.attempted);
    }
    private static WirelessBatchCadence<String> learnedCadence(IPatternDetails pattern) {
        var cadence=new WirelessBatchCadence<String>();
        cadence.recordSuccess("target",pattern,0,2000,false);
        assertFalse(cadence.usesBulkRefill("target",pattern,2000));
        for(int tick=5;tick<=20;tick+=5)
            cadence.recordSuccess("target",pattern,tick,512,false,ProviderTarget.BaselineStatus.PREFIX_COMPLETE);
        assertTrue(cadence.usesBulkRefill("target",pattern,2000));
        return cadence;
    }
    @Test void suppressedTailIsReplayedOnlyForProvenTimedRefill() {
        var f = learnSegmentedReservoir();
        f.machine.stored = 0;
        var probeCalls = new ArrayList<Integer>();
        var early = f.target.pushPatternStep(f.pattern, 10000, 31, true, true,
                () -> false, copies -> {
                    probeCalls.add(copies);
                    return probeCalls.size() == 3 ? ProviderTarget.BatchChunk.REJECTED
                            : new ProviderTarget.BatchChunk(copies, true, false);
                });
        assertEquals(1024, early.ownedCopies());
        assertEquals(ProviderTarget.BaselineStatus.RESERVOIR_PREFIX_COMPLETE, early.baselineStatus());
        f.machine.attempted.clear();
        f.target.preferSegmentedReservoirRefill(f.pattern, true);
        var refill = f.target.pushPatternStep(f.pattern, 10000, 50, true, true,
                () -> false, f.machine::push);
        assertEquals(List.of(512, 512, 976), f.machine.attempted);
        assertEquals(2000, refill.ownedCopies());
    }
    @Test void controlCallerLimitedSuccessUsesOrdinaryCadenceWhenNotBulk() {
        var p=new ProviderSchedulerRetentionTest.Pattern();var c=new WirelessBatchCadence<String>();
        c.recordSuccess("target",p,0,2000,false);
        int delay=c.recordSuccess("target",p,10,512,true);
        System.out.println("CONTROL ordinary limited delay="+delay);assertTrue(delay<=5);
    }
    @Test void callerLimitedSuccessMustNotRetainFullBulkWait() {
        var p=new ProviderSchedulerRetentionTest.Pattern();var c=learnedCadence(p);
        int delay=c.recordSuccess("target",p,25,512,true);
        System.out.println("caller-limited delay="+delay+" bulk="+c.usesBulkRefill("target",p,2000));
        assertTrue(delay<=5,"512-copy caller-limited success still waits full bulk period: "+delay);
        assertFalse(c.usesBulkRefill("target", p, 2000));
        for (int tick = 30; tick <= 60; tick += 5) {
            assertTrue(c.recordSuccess("target", p, tick, 512, true) <= 5);
            assertFalse(c.usesBulkRefill("target", p, 2000));
        }
    }
    @Test void controlColdPatternIsExpiredWithoutCatalogReset() throws Exception {
        var d=new ProviderWirelessDispatch();var ts=ProviderSchedulerRetentionTest.targets(8);
        var cold=new ProviderSchedulerRetentionTest.Pattern();var hot=new ProviderSchedulerRetentionTest.Pattern();
        ProviderSchedulerRetentionTest.dispatchTick(d,ts,cold,0);
        for(int tick=1;tick<=240;tick++)ProviderSchedulerRetentionTest.dispatchTick(d,ts,hot,tick);
        assertEquals(8,ProviderSchedulerRetentionTest.retained(d,ts).physicalPairs());
    }
    @Test void catalogChangesMustKeepColdHistoryIndexedForExpiry() throws Exception {
        var d=new ProviderWirelessDispatch();var ts=ProviderSchedulerRetentionTest.targets(8);
        for(int tick=0;tick<20;tick++) {
            ProviderSchedulerRetentionTest.dispatchTick(d,ts,new ProviderSchedulerRetentionTest.Pattern(),tick);
            d.patternsChanged();
        }
        var hot=new ProviderSchedulerRetentionTest.Pattern();
        for(int tick=20;tick<=240;tick++)ProviderSchedulerRetentionTest.dispatchTick(d,ts,hot,tick);
        var retained=ProviderSchedulerRetentionTest.retained(d,ts);
        System.out.println("catalog churn retained="+retained);
        assertEquals(8,retained.physicalPairs(),"Only eight hot target histories should survive TTL, not removed patterns");
    }

    @Test void preferredRefillNeverExceedsCallerAllowance() {
        var f = learnSegmentedReservoir();
        f.machine.stored = 0;
        f.machine.attempted.clear();
        f.target.preferSegmentedReservoirRefill(f.pattern, true);
        var result = f.target.pushPatternStep(f.pattern, 1536, 31, true, true,
                () -> false, f.machine::push);
        assertEquals(List.of(512, 512, 512), f.machine.attempted);
        assertEquals(1536, result.ownedCopies());
        assertTrue(result.requestLimited());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"rejected", "partial", "abort"})
    void segmentedRefillStopsAtFailedSegmentAndKeepsPriorOwnership(String failure) {
        var f = learnSegmentedReservoir();
        var calls = new ArrayList<Integer>();
        f.target.preferSegmentedReservoirRefill(f.pattern, true);
        var result = f.target.pushPatternStep(f.pattern, 10000, 31, true, true,
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

    @Test void catalogChangesDoNotRestartIdleExpiryOrLoseMaintenanceWork() throws Exception {
        var wakeups = new java.util.concurrent.atomic.AtomicInteger();
        var saves = new java.util.concurrent.atomic.AtomicInteger();
        var d = new ProviderWirelessDispatch(wakeups::incrementAndGet, saves::incrementAndGet);
        var targets = ProviderSchedulerRetentionTest.targets(8);
        for (int p = 0; p < 20; p++)
            ProviderSchedulerRetentionTest.dispatchTick(d, targets, new ProviderSchedulerRetentionTest.Pattern(), 0);
        assertEquals(1, wakeups.get());
        for (int tick = 1; tick <= 100; tick++) {
            d.patternsChanged();
            assertTrue(d.hasMaintenanceWork());
            d.maintain(tick);
        }
        assertEquals(160, ProviderSchedulerRetentionTest.retained(d, targets).physicalPairs());
        d.maintain(101);
        assertEquals(128, ProviderSchedulerRetentionTest.retained(d, targets).physicalPairs());
        d.maintain(101);
        assertEquals(128, ProviderSchedulerRetentionTest.retained(d, targets).physicalPairs());
        for (int tick = 102; tick <= 105; tick++) d.maintain(tick);
        assertEquals(0, ProviderSchedulerRetentionTest.retained(d, targets).physicalPairs());
        assertFalse(d.hasMaintenanceWork());
        assertEquals(5, saves.get());
    }
}
