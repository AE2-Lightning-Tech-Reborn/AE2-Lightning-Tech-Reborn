package com.moakiee.ae2lt.logic.energy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WirelessEnergyScheduleTest {
    private static final class Entry { long age; }

    @Test void visitsMatchHeapThroughDenialsMutationsAndCompleteSweeps() {
        var random = new Random(0xFE20261024L);
        for (int count : new int[] {1, 17, 64, 128, 129, 257, 8192}) {
            var entries = new ArrayList<Entry>();
            for (int i = 0; i < count; i++) entries.add(new Entry());
            var actual = new WirelessEnergySchedule<Entry>(e -> e.age);
            var expected = new PriorityQueue<Entry>(Comparator.comparingLong(e -> e.age));
            actual.rebuild(entries); expected.addAll(entries);
            long age = 0;
            for (int tick = 0; tick < 500; tick++) {
                if (tick == 200 || tick == 350) {
                    entries.remove(random.nextInt(entries.size()));
                    entries.add(new Entry());
                    actual.rebuild(entries); expected.clear(); expected.addAll(entries);
                }
                long previousAge = age;
                int visits = Math.min(entries.size(), tick % 7 == 0 ? entries.size() : 128);
                var visited = new ArrayList<Entry>();
                for (int i = 0; i < visits; i++) {
                    var entry = expected.remove();
                    assertSame(entry, actual.remove(), "visit order differs at count=" + count + ", tick=" + tick);
                    if (random.nextInt(3) != 0) entry.age = ++age;
                    visited.add(entry);
                }
                expected.addAll(visited); actual.requeue(visited, previousAge);
                assertEquals(expected.size(), actual.size());
            }
        }
    }
}
