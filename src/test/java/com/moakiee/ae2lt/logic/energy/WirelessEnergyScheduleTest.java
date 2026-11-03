package com.moakiee.ae2lt.logic.energy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WirelessEnergyScheduleTest {
    private static final class Entry { long due; int visits; }

    @Test void sleepingTargetsAreNotPolledAndBacklogCannotStarve() {
        var random = new Random(0xFE20261024L);
        for (int count : new int[] {1, 17, 64, 128, 129, 257, 8192}) {
            var entries = new ArrayList<Entry>();
            for (int i = 0; i < count; i++) entries.add(new Entry());
            var actual = new WirelessEnergySchedule<Entry>(e -> e.due);
            actual.rebuild(entries, 0);
            for (int tick = 0; tick < 500; tick++) {
                if (tick == 200 || tick == 350) {
                    entries.remove(random.nextInt(entries.size()));
                    entries.add(new Entry());
                    actual.rebuild(entries, tick);
                }
                actual.advance(tick);
                int visits = Math.min(actual.readyCount(), tick % 7 == 0 ? entries.size() : 128);
                var visited = new ArrayList<Entry>();
                var unique = new HashSet<Entry>();
                for (int i = 0; i < visits; i++) {
                    var entry = actual.remove();
                    assertTrue(unique.add(entry), "endpoint visited twice in one tick");
                    assertTrue(entry.due <= tick, "sleeping endpoint polled");
                    entry.due = tick + 1 + random.nextInt(WirelessEnergySchedule.MAX_DELAY);
                    entry.visits++;
                    visited.add(entry);
                }
                for (var entry : visited) actual.schedule(entry);
            }
            assertTrue(entries.stream().allMatch(e -> e.visits > 0), "endpoint starved");
        }
    }

    @Test void overdueWorkPrecedesNewlyDueAndLongPausesDoNotLoseEntries() {
        var backlog = new Entry();
        var active = new Entry(); active.due = 1;
        var idle = new Entry(); idle.due = 20;
        var actual = new WirelessEnergySchedule<Entry>(e -> e.due);
        actual.rebuild(java.util.List.of(backlog, active, idle), 0);
        actual.advance(1);
        assertSame(backlog, actual.remove());
        backlog.due = 2; actual.schedule(backlog);
        actual.advance(2);
        assertSame(active, actual.remove());
        assertSame(backlog, actual.remove());
        assertEquals(0, actual.readyCount());
        actual.advance(1000);
        assertSame(idle, actual.remove());
        assertEquals(0, actual.readyCount());
    }

    @Test void continuousMutationsPreserveUnservedBacklogOrder() {
        var entries = new ArrayList<Entry>();
        for (int i = 0; i < 100; i++) entries.add(new Entry());
        var actual = new WirelessEnergySchedule<Entry>(e -> e.due);
        actual.rebuild(entries, 0);
        for (int tick = 0; tick < 100; tick++) {
            entries.add(0, new Entry());
            actual.rebuild(entries, tick);
            actual.advance(tick);
            var next = actual.remove();
            next.visits++;
            next.due = tick + 1;
            actual.schedule(next);
        }
        assertTrue(entries.stream().skip(100).allMatch(e -> e.visits == 1),
                "new bindings starved the original backlog");
    }
}
