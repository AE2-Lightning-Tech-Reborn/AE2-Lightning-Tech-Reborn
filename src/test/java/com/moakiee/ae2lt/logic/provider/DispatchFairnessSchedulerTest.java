package com.moakiee.ae2lt.logic.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class DispatchFairnessSchedulerTest {

    private static final String PATTERN = "pattern";
    private static final List<String> FOUR_TARGETS =
            List.of("a", "b", "c", "d");

    @Test
    void allowanceCountsOwnedBatchCopiesRatherThanPushCalls() {
        var scheduler = new DispatchFairnessScheduler<String, String>();

        dispatchFullPass(scheduler, FOUR_TARGETS, 0L);
        assertCounts(scheduler, 0L, 1L, 1L, 1L, 1L);

        dispatchFullPass(scheduler, FOUR_TARGETS, 1L);
        assertCounts(scheduler, 1L, 2L, 2L, 2L, 2L);

        try (var pass = scheduler.beginPass(
                PATTERN, FOUR_TARGETS, 1L, 2L)) {
            for (int i = 0; i < 2; i++) {
                var target = pass.poll();
                assertEquals(2L, pass.allowance(target));
                pass.success(target, 2L);
            }
        }

        assertEquals(2L, scheduler.minimumActiveDispatchCount(PATTERN, 2L));
        assertEquals(4L, scheduler.maximumActiveDispatchCount(PATTERN, 2L));
    }

    @Test
    void onePassNeverLeasesTheSameMachineTwice() {
        var scheduler = new DispatchFairnessScheduler<String, String>();

        try (var pass = scheduler.beginPass(
                PATTERN, FOUR_TARGETS, 1L, 0L)) {
            for (int i = 0; i < FOUR_TARGETS.size(); i++) {
                var target = pass.poll();
                pass.success(target, 1L);
            }
            assertEquals(null, pass.poll());
        }
    }

    @Test
    void successfulCopiesCannotExceedTheLeasedAllowance() {
        var scheduler = new DispatchFairnessScheduler<String, String>();

        try (var pass = scheduler.beginPass(
                PATTERN, FOUR_TARGETS, 1L, 0L)) {
            var target = pass.poll();
            assertEquals(1L, pass.allowance(target));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> pass.success(target, 2L));
        }
    }

    @Test
    void cooledTargetReturnsWithoutFabricatingOwnedCopies() {
        var scheduler = new DispatchFairnessScheduler<String, String>();
        var targets = List.of("fast", "slow");

        dispatchFullPass(scheduler, targets, 0L);
        dispatchFullPass(scheduler, targets, 1L);

        try (var pass = scheduler.beginPass(PATTERN, targets, 1L, 2L)) {
            var first = pass.poll();
            var second = pass.poll();
            if ("slow".equals(first)) {
                pass.cooldown(first, 10L);
                pass.success(second, pass.allowance(second));
            } else {
                pass.success(first, pass.allowance(first));
                pass.cooldown(second, 10L);
            }
        }

        assertEquals(1, scheduler.activeTargetCount(PATTERN, 9L));
        long activeCount = scheduler.maximumActiveDispatchCount(PATTERN, 9L);

        try (var ignored = scheduler.beginPass(PATTERN, targets, 1L, 10L)) {
            assertEquals(2, ignored.activeTargetsAtStart());
        }
        assertEquals(2L, scheduler.dispatchCount(PATTERN, "slow", 10L));
        assertEquals(4L, activeCount);
    }

    @Test
    void coveragePreservesSynchronizedMachinePhase() {
        var scheduler = new DispatchFairnessScheduler<String, String>();

        try (var pass = scheduler.beginPass(
                PATTERN, FOUR_TARGETS, 1L, 0L)) {
            String target;
            while ((target = pass.poll()) != null) {
                pass.successAndCover(target, 1L, 2);
            }
        }

        assertEquals(0, scheduler.activeTargetCount(PATTERN, 1L));
        try (var pass = scheduler.beginPass(
                PATTERN, FOUR_TARGETS, 1L, 1L)) {
            assertEquals(null, pass.poll());
        }

        assertEquals(4, scheduler.activeTargetCount(PATTERN, 2L));
        var refillWave = new java.util.HashSet<String>();
        try (var pass = scheduler.beginPass(
                PATTERN, FOUR_TARGETS, 1L, 2L)) {
            String target;
            while ((target = pass.poll()) != null) {
                refillWave.add(target);
                pass.successAndCover(target, 1L, 2);
            }
        }

        assertEquals(4, refillWave.size());
    }

    @Test
    void normalRefillWaitDoesNotAddAverageCreditOrHideTheLeastServedTarget() {
        var scheduler = new DispatchFairnessScheduler<String, String>();
        var targets = List.of("a", "b", "c");
        try (var pass = scheduler.beginPass(PATTERN, targets, 1L, 0L)) {
            for (int i = 0; i < 3; i++) {
                var target = pass.poll();
                pass.raiseAllowance(target, 100);
                pass.successAndCover(target, 100, 1);
            }
        }
        try (var pass = scheduler.beginPass(PATTERN, targets, 1L, 1L)) {
            for (int i = 0; i < 3; i++) {
                var target = pass.poll();
                pass.successAndCover(target, target.equals("c") ? 1 : 100, 1);
            }
        }
        try (var pass = scheduler.beginPass(PATTERN, targets, 1L, 2L)) {
            assertEquals("c", pass.poll());
            assertEquals(101, pass.allowance("c"));
            assertEquals(101, scheduler.dispatchCount(PATTERN, "c", 2L));
        }
    }

    @Test
    void coveredTargetStillBoundsNewAllocationsUntilItsWindowCountsExpire() {
        var scheduler = new DispatchFairnessScheduler<String, String>();
        var targets = List.of("a", "b");
        try (var pass = scheduler.beginPass(PATTERN, targets, 1L, 0L)) {
            for (int i = 0; i < 2; i++) {
                var target = pass.poll();
                pass.raiseAllowance(target, 100);
                pass.successAndCover(target, 100, target.equals("a") ? 20 : 1);
            }
        }
        try (var pass = scheduler.beginPass(PATTERN, targets, 1L, 1L)) {
            assertEquals("b", pass.poll());
            assertEquals(100, pass.allowance("b"));
            assertEquals(100, pass.raiseRampAllowance("b", 1000));
            pass.successAndCover("b", 100, 1);
        }
        try (var pass = scheduler.beginPass(PATTERN, targets, 1L, 2L)) {
            assertEquals(null, pass.poll(), "b already has twice a's window count");
        }
        try (var pass = scheduler.beginPass(PATTERN, targets, 1L, 100L)) {
            assertEquals("a", pass.poll());
            assertEquals(2, pass.allowance("a"));
        }
    }

    @Test
    void everyAcceptedAllocationKeepsActiveMaximumWithinTwiceMinimum() {
        var scheduler = new DispatchFairnessScheduler<Integer, String>();
        var targets = java.util.stream.IntStream.range(0, 32)
                .boxed()
                .toList();

        for (long tick = 0L; tick < 100L; tick++) {
            long remaining = 1L + (tick * 37L) % 300L;
            try (var pass = scheduler.beginPass(
                    PATTERN, targets, 1L, tick)) {
                Integer target;
                while (remaining > 0L && (target = pass.poll()) != null) {
                    long accepted = Math.min(remaining, pass.allowance(target));
                    if (accepted > 0L) {
                        pass.success(target, accepted);
                        remaining -= accepted;
                    }
                    assertActiveRatio(scheduler, tick);
                }
            }
            assertActiveRatio(scheduler, tick);
        }
    }

    @Test
    void dispatchCopiesExpireAfterOneHundredTicks() {
        var scheduler = new DispatchFairnessScheduler<String, String>();

        dispatchFullPass(scheduler, FOUR_TARGETS, 99L);
        assertEquals(1L, scheduler.maximumActiveDispatchCount(PATTERN, 99L));
        assertEquals(1L, scheduler.maximumActiveDispatchCount(PATTERN, 198L));
        assertEquals(0L, scheduler.maximumActiveDispatchCount(PATTERN, 199L));
        assertEquals(0L, scheduler.minimumActiveDispatchCount(PATTERN, 199L));
    }

    @Test
    void recycledWindowsAndQueueEntriesPreservePhysicalCountsAcrossLifecycleChanges() {
        var scheduler = new DispatchFairnessScheduler<Integer, String>();
        var targets = java.util.stream.IntStream.range(0, 24).boxed().toList();
        var receipts = new java.util.ArrayList<long[]>();
        var random = new java.util.Random(20261022);
        long tick = 0;
        for (int step = 0; step < 1800; step++) {
            long next = step % 173 == 172 ? tick - 150 : tick + random.nextInt(4);
            if (next < tick) receipts.clear();
            tick = next;
            long now = tick;
            receipts.removeIf(receipt -> receipt[0] <= now - 100);
            int changed = random.nextInt(targets.size());
            if (step % 11 == 0) {
                scheduler.removeTarget(changed);
                receipts.removeIf(receipt -> receipt[1] == changed);
            }
            if (step % 7 == 0) scheduler.pauseTarget(changed);
            if (step % 9 == 0) scheduler.resumeTarget(changed, tick);
            try (var pass = scheduler.beginPass(PATTERN, targets, step, tick)) {
                Integer target;
                var visited = new java.util.HashSet<Integer>();
                while ((target = pass.poll()) != null) {
                    assertTrue(visited.add(target), "one pass leased a target twice");
                    if (random.nextInt(5) == 0) {
                        pass.cooldown(target, tick + 1 + random.nextInt(12));
                    } else {
                        long copies = 1 + random.nextInt(100);
                        pass.raiseAllowance(target, copies);
                        if (random.nextBoolean()) pass.successAndCover(target, copies, 1 + random.nextInt(8));
                        else pass.success(target, copies);
                        receipts.add(new long[]{tick, target, copies});
                    }
                }
            }
            for (int target : targets) {
                long expected = 0;
                for (var receipt : receipts) if (receipt[1] == target) expected += receipt[2];
                assertEquals(expected, scheduler.dispatchCount(PATTERN, target, tick),
                        "physical receipt ledger differs at step " + step + " target " + target);
            }
        }
    }

    @Test
    void recycledCooldownEntryCannotWakeAnObsoleteTargetOrDeadline() {
        var scheduler = new DispatchFairnessScheduler<String, String>();
        for (int tick = 0; tick < 300; tick++) {
            scheduler.excludeUntil(PATTERN, "a", tick + 1, tick);
            scheduler.excludeUntil(PATTERN, "a", tick + 20, tick);
            assertEquals(0, scheduler.activeTargetCount(PATTERN, tick + 1));
            scheduler.removeTarget("a");
            try (var pass = scheduler.beginPass(PATTERN, List.of("a"), tick, tick + 1)) {
                assertEquals("a", pass.poll());
                pass.success("a", 1);
                assertEquals(null, pass.poll());
            }
        }
    }

    private static <T> void assertActiveRatio(
            DispatchFairnessScheduler<T, String> scheduler,
            long tick) {
        long minimum = scheduler.minimumActiveDispatchCount(PATTERN, tick);
        long maximum = scheduler.maximumActiveDispatchCount(PATTERN, tick);
        assertTrue(maximum <= 2L * Math.max(1L, minimum),
                () -> "active dispatch ratio exceeded 2:1: "
                        + minimum + ".." + maximum);
    }

    private static void dispatchFullPass(
            DispatchFairnessScheduler<String, String> scheduler,
            List<String> targets,
            long tick) {
        try (var pass = scheduler.beginPass(PATTERN, targets, 1L, tick)) {
            String target;
            while ((target = pass.poll()) != null) {
                long allowance = pass.allowance(target);
                pass.success(target, allowance);
            }
        }
    }

    private static void assertCounts(
            DispatchFairnessScheduler<String, String> scheduler,
            long tick,
            long a,
            long b,
            long c,
            long d) {
        assertEquals(a, scheduler.dispatchCount(PATTERN, "a", tick));
        assertEquals(b, scheduler.dispatchCount(PATTERN, "b", tick));
        assertEquals(c, scheduler.dispatchCount(PATTERN, "c", tick));
        assertEquals(d, scheduler.dispatchCount(PATTERN, "d", tick));
    }

}
