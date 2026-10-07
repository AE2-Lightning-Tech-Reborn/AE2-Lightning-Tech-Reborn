package com.moakiee.ae2lt.logic.provider;

import static org.junit.jupiter.api.Assertions.*;
import static com.moakiee.ae2lt.logic.provider.WirelessOverflowQueue.OverflowAttemptResult.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;

class WirelessOverflowCadenceTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void initialTickOrderingCollisionDoesNotThrottleAContinuousConsumer() {
        var fixture = new Fixture();
        fixture.attempt(1, false);
        assertEquals(2, fixture.queue.nextDueTick());
        for (int tick = 2; tick < 200; tick++) {
            fixture.attempt(tick, true);
            assertEquals(tick + 1, fixture.queue.nextDueTick());
        }
    }

    @Test
    void learnsDifferentMachinePeriodsWithoutLosingProcessingCycles() {
        for (int period : new int[] {1, 2, 5, 6, 10, 20, 60}) {
            var fixture = new Fixture();
            int stock = 64;
            long received = 0, possible = 0, attempts = 0;
            for (int tick = 1; tick < 5000; tick++) {
                if (tick % period == 0) {
                    if (tick >= 200) {
                        received += stock;
                        possible += 64;
                    }
                    stock = 0;
                }
                if (fixture.queue.nextDueTick() <= tick) {
                    if (tick >= 200) attempts++;
                    fixture.attempt(tick, stock == 0);
                    stock = 64;
                }
            }
            System.out.println("OVERFLOW_CADENCE period=" + period + " received=" + received
                    + " possible=" + possible + " attempts=" + attempts);
            assertEquals(possible, received, "missed machine cycle, period=" + period);
            assertTrue(attempts <= 2 * ((5000 - 200) / period + 1),
                    "more than two polls per cycle after learning, period=" + period);
        }
    }

    @Test
    void adaptsWhenTheSameMachineChangesSpeedRepeatedly() {
        var fixture = new Fixture();
        int stock = 64;
        int[] periods = {20, 1, 6, 60, 5, 10, 2};
        long[] actual = new long[periods.length], possible = new long[periods.length];
        for (int tick = 1; tick < periods.length * 500; tick++) {
            int stage = tick / 500;
            if (tick % periods[stage] == 0) {
                if (tick % 500 >= 100) {
                    actual[stage] += stock;
                    possible[stage] += 64;
                }
                stock = 0;
            }
            if (fixture.queue.nextDueTick() <= tick) {
                fixture.attempt(tick, stock == 0);
                stock = 64;
            }
        }
        assertArrayEquals(possible, actual);
    }

    @Test
    void permanentlyFullTargetSleepsAndRecoversAfterConsumptionResumes() {
        var fixture = new Fixture();
        int attempts = 0;
        for (int tick = 1; tick < 10000; tick++) {
            if (fixture.queue.nextDueTick() > tick) continue;
            fixture.attempt(tick, false);
            attempts++;
        }
        assertTrue(attempts < 620, "bounded discovery window followed by idle polling: " + attempts);
        long due = fixture.queue.nextDueTick();
        assertTrue(due < 10020);
        fixture.attempt(due, true);
        assertEquals(due + 1, fixture.queue.nextDueTick());
        fixture.attempt(due + 1, true);
        assertEquals(due + 2, fixture.queue.nextDueTick());
    }

    @Test
    void offlineTimeIsNotLearnedAsAConsumptionPeriod() {
        var fixture = new Fixture();
        fixture.attempt(1, true);
        for (long tick = 2; tick < 500; tick = fixture.queue.nextDueTick()) {
            assertEquals(fixture.connection, fixture.queue.pollDue(tick));
            fixture.queue.rescheduleUnavailable(fixture.connection, fixture.bucket, tick);
        }
        long online = fixture.queue.nextDueTick();
        fixture.attempt(online, false);
        assertEquals(online + 1, fixture.queue.nextDueTick());
        fixture.attempt(online + 1, true);
        assertEquals(online + 2, fixture.queue.nextDueTick());
    }

    @Test
    void overdueBudgetDoesNotTrainASlowerMachinePeriod() {
        var fixture = new Fixture();
        fixture.attempt(1, true);
        fixture.attempt(2, false);
        fixture.attempt(30, true);
        assertEquals(31, fixture.queue.nextDueTick());
    }

    @Test
    void busyTargetsShareTheExistingBudgetAndAllRetainOwnership() {
        var queue = new WirelessOverflowQueue();
        int[] visits = new int[1024];
        for (int i = 0; i < visits.length; i++) {
            queue.restoreBucket(connection(i), WirelessOverflowQueue.Bucket.fallback((short) 0, List.of()), 0);
        }
        for (int tick = 1; tick <= 64; tick++) {
            assertTrue(queue.beginFlush(tick));
            for (int budget = 0; budget < 64; budget++) {
                var target = queue.pollDue(tick);
                assertNotNull(target);
                visits[target.pos().getX()]++;
                queue.reschedule(target, queue.get(target), tick, PROGRESSED);
            }
            assertFalse(queue.beginFlush(tick));
        }
        for (int count : visits) assertEquals(4, count);
        assertEquals(1024, queue.size());
        var target = connection(0);
        var oldBucket = queue.remove(target);
        assertNotNull(oldBucket);
        assertNull(queue.get(target));
        queue.restoreBucket(target, WirelessOverflowQueue.Bucket.fallback((short) 0, List.of()), 64);
        // A stale attempt may not reschedule the replacement bucket.
        queue.reschedule(target, oldBucket, 64, PROGRESSED);
        assertEquals(65, queue.get(target).dueTick);
    }

    private static WirelessConnection connection(int index) {
        return new WirelessConnection(Level.OVERWORLD, new BlockPos(index, 0, 0), Direction.NORTH);
    }

    private static final class Fixture {
        final WirelessOverflowQueue queue = new WirelessOverflowQueue();
        final WirelessConnection connection = connection(0);
        final WirelessOverflowQueue.Bucket bucket = WirelessOverflowQueue.Bucket.fallback((short) 0, List.of());

        Fixture() { queue.restoreBucket(connection, bucket, 0); }

        void attempt(long tick, boolean progressed) {
            assertEquals(connection, queue.pollDue(tick));
            queue.reschedule(connection, bucket, tick, progressed ? PROGRESSED : BLOCKED);
        }
    }
}
