package com.moakiee.ae2lt.logic.energy;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.ToLongFunction;

/**
 * Age-ordered visits with O(1) steady-state rotation. New entries have age zero;
 * every granted turn receives a unique, increasing positive age. Until all zero
 * ages have advanced, the heap preserves the original scheduler's tie ordering.
 */
final class WirelessEnergySchedule<T> {
    private final ToLongFunction<T> age;
    private final PriorityQueue<T> initial;
    private final ArrayDeque<T> ordered = new ArrayDeque<>();
    private boolean rotating;

    WirelessEnergySchedule(ToLongFunction<T> age) {
        this.age = age;
        initial = new PriorityQueue<>(Comparator.comparingLong(age));
    }

    void rebuild(Collection<T> entries) {
        initial.clear();
        ordered.clear();
        initial.addAll(entries);
        rotating = false;
        startRotation();
    }

    int size() { return rotating ? ordered.size() : initial.size(); }

    T remove() { return rotating ? ordered.removeFirst() : initial.remove(); }

    /** Reinsert after the whole visit window; no endpoint can run twice in that window. */
    void requeue(List<T> visited, long previousAge) {
        if (!rotating) {
            initial.addAll(visited);
            startRotation();
            return;
        }
        // Granted turns form a new, increasing suffix. Denied turns retain their
        // original order ahead of all unvisited entries, even at sweep boundaries.
        for (int i = 0; i < visited.size(); i++) {
            T entry = visited.get(i);
            if (age.applyAsLong(entry) > previousAge) ordered.addLast(entry);
        }
        for (int i = visited.size() - 1; i >= 0; i--) {
            T entry = visited.get(i);
            if (age.applyAsLong(entry) <= previousAge) ordered.addFirst(entry);
        }
    }

    private void startRotation() {
        if (!initial.isEmpty() && age.applyAsLong(initial.element()) > 0) {
            while (!initial.isEmpty()) ordered.addLast(initial.remove());
            rotating = true;
        }
    }
}
