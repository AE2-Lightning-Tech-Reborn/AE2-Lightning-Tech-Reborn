package com.moakiee.ae2lt.logic.energy;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.ToLongFunction;

/** Constant-time timing wheel. Overdue work stays ahead of newly due endpoints. */
final class WirelessEnergySchedule<T> {
    static final int MAX_DELAY = 20;
    private static final int SLOTS = 32, MASK = SLOTS - 1;
    private final ArrayDeque<T> ready = new ArrayDeque<>();
    private final ArrayDeque<T>[] wheel;
    private final ToLongFunction<T> due;
    private long tick;

    @SuppressWarnings("unchecked")
    WirelessEnergySchedule(ToLongFunction<T> due) {
        this.due = due;
        wheel = new ArrayDeque[SLOTS];
        for (int i = 0; i < SLOTS; i++) wheel[i] = new ArrayDeque<>();
    }

    void rebuild(Collection<T> entries, long now) {
        var remaining = new HashSet<>(entries);
        // A mutation must not move the oldest, unserved backlog behind new work.
        ready.removeIf(entry -> !remaining.remove(entry));
        for (var slot : wheel) slot.clear();
        tick = now;
        for (T entry : entries) {
            if (!remaining.remove(entry)) continue;
            long next = due.applyAsLong(entry);
            if (next <= now) ready.addLast(entry);
            else wheel[(int) next & MASK].addLast(entry);
        }
    }

    void advance(long now) {
        // All delays fit in the wheel. A long pause needs only one full rotation.
        long steps = Math.min(SLOTS, Math.max(0, now - tick));
        for (int i = 1; i <= steps; i++) {
            var slot = wheel[(int) (tick + i) & MASK];
            while (!slot.isEmpty()) ready.addLast(slot.removeFirst());
        }
        tick = now;
    }

    int readyCount() { return ready.size(); }
    T remove() { return ready.removeFirst(); }

    /** Requeue only after dispatch; an endpoint cannot run twice in the same tick. */
    void schedule(T entry) {
        long next = due.applyAsLong(entry);
        if (next <= tick || next - tick > MAX_DELAY) throw new IllegalArgumentException("Invalid endpoint delay");
        wheel[(int) next & MASK].addLast(entry);
    }
}
