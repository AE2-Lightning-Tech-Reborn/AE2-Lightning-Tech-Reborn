package com.moakiee.ae2lt.logic.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

class ProviderReturnSweepTest {
    @Test
    void repeatedSnapshotQueriesDoNotTraverseTargetsOrRestartTheRound() {
        var entries = List.of(target(0), target(1));
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var snapshot = new java.util.AbstractList<ProviderTarget>() {
            @Override public ProviderTarget get(int index) { reads.incrementAndGet(); return entries.get(index); }
            @Override public int size() { return entries.size(); }
        };
        var sweep = new ProviderReturnSweep();
        sweep.synchronize(snapshot, 0);
        reads.set(0);
        for (int i = 0; i < 1000; i++) {
            sweep.synchronize(snapshot, i);
            assertEquals(9L, sweep.nextDueTick());
        }
        assertEquals(0, reads.get());
        assertSame(entries.get(0), sweep.pollDue(9));
        sweep.recordPeriodic(entries.get(0), 9, OutputReturnResult.EMPTY);
        sweep.synchronize(List.copyOf(entries), 10);
        assertEquals(19L, sweep.nextDueTick(), "equivalent snapshots preserve progress");
        sweep.clear();
        sweep.synchronize(snapshot, 100);
        assertEquals(109L, sweep.nextDueTick(), "clear must release the snapshot too");
    }

    @Test
    void emptyRoundsBackOffProviderTo128Ticks() {
        var sweep = new ProviderReturnSweep();
        var target = target(0);
        sweep.synchronize(List.of(target), 0L);

        long tick = sweep.nextDueTick();
        int[] expected = {40, 80, 128, 128};
        for (int interval : expected) {
            assertSame(target, sweep.pollDue(tick));
            sweep.recordPeriodic(target, tick, OutputReturnResult.EMPTY);
            assertEquals(interval, sweep.interval());
            long nextTick = sweep.nextDueTick();
            assertEquals(interval, nextTick - tick);
            tick = nextTick;
        }
    }

    @Test
    void outputOrDispatchRestoresActiveInterval() {
        var sweep = new ProviderReturnSweep();
        var first = target(0);
        var second = target(1);
        sweep.synchronize(List.of(first, second), 0L);

        assertSame(first, sweep.pollDue(9L));
        sweep.recordPeriodic(first, 9L, OutputReturnResult.EMPTY);
        assertSame(second, sweep.pollDue(19L));
        sweep.recordPeriodic(second, 19L, OutputReturnResult.EMPTY);
        assertEquals(40, sweep.interval());

        long dueTick = sweep.nextDueTick();
        var due = sweep.pollDue(dueTick);
        sweep.recordPeriodic(due, dueTick, OutputReturnResult.EXTRACTED);
        assertEquals(20, sweep.interval());

        sweep.recordDispatch(first, dueTick + 1L);
        assertEquals(20, sweep.interval());
    }

    @Test
    void dispatchAcceleratesUnfinishedIdleRoundWithoutRestartingIt() {
        var sweep = new ProviderReturnSweep();
        var first = target(0);
        var second = target(1);
        sweep.synchronize(List.of(first, second), 0L);

        sweep.recordPeriodic(
                sweep.pollDue(9L), 9L, OutputReturnResult.EMPTY);
        sweep.recordPeriodic(
                sweep.pollDue(19L), 19L, OutputReturnResult.EMPTY);
        assertEquals(40, sweep.interval());

        sweep.recordDispatch(first, 25L);

        assertEquals(20, sweep.interval());
        assertNull(sweep.pollDue(43L));
        assertSame(second, sweep.pollDue(44L));
        sweep.recordPeriodic(second, 44L, OutputReturnResult.EMPTY);
        assertEquals(20, sweep.interval());
    }

    @Test
    void blockedOutputKeepsProviderAtActiveInterval() {
        var sweep = new ProviderReturnSweep();
        var target = target(0);
        sweep.synchronize(List.of(target), 0L);

        long dueTick = sweep.nextDueTick();
        sweep.recordPeriodic(
                sweep.pollDue(dueTick),
                dueTick,
                OutputReturnResult.BLOCKED);

        assertEquals(20, sweep.interval());
    }

    @Test
    void roundIsSpreadAcrossConfiguredInterval() {
        var sweep = new ProviderReturnSweep();
        var targets = new ArrayList<ProviderTarget>();
        for (int i = 0; i < 1_024; i++) {
            targets.add(target(i));
        }
        sweep.synchronize(targets, 100L);

        int total = 0;
        for (long tick = 100L; tick < 120L; tick++) {
            int thisTick = 0;
            ProviderTarget due;
            while ((due = sweep.pollDue(tick)) != null) {
                thisTick++;
                total++;
                sweep.recordPeriodic(due, tick, OutputReturnResult.EMPTY);
            }
            org.junit.jupiter.api.Assertions.assertTrue(
                    thisTick == 51 || thisTick == 52);
        }
        assertEquals(1_024, total);
        assertNull(sweep.pollDue(119L));
    }

    private static ProviderTarget target(int x) {
        return new ProviderTarget(
                Level.OVERWORLD,
                new BlockPos(x, 64, 0),
                Direction.NORTH);
    }
}
