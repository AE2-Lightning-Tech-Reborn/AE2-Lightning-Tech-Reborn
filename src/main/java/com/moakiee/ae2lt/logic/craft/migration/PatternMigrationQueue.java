package com.moakiee.ae2lt.logic.craft.migration;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.ToIntFunction;

/** Exhaust each priority tier, rotating sources after 32 slots rather than draining one warehouse. */
public final class PatternMigrationQueue<T> {
    public static final int QUANTUM = 32;
    private final List<ArrayDeque<Cursor<T>>> tiers = List.of(
            new ArrayDeque<>(), new ArrayDeque<>(), new ArrayDeque<>(), new ArrayDeque<>());
    private final ToIntFunction<T> size;
    private Cursor<T> current;
    private int tier;
    private int remainingQuantum;

    public PatternMigrationQueue(ToIntFunction<T> size) {
        this.size = size;
    }

    public void add(int priority, T source) {
        tiers.get(priority).addLast(new Cursor<>(source));
        tier = Math.min(tier, priority);
    }

    public Step<T> next() {
        while (tier < tiers.size()) {
            if (current == null) {
                current = tiers.get(tier).pollFirst();
                remainingQuantum = QUANTUM;
                if (current == null) { tier++; continue; }
            }
            if (current.slot >= size.applyAsInt(current.source)) {
                current = null;
                continue;
            }
            var step = new Step<>(current.source, current.slot++, tier + 1);
            if (--remainingQuantum == 0) {
                tiers.get(tier).addLast(current);
                current = null;
            }
            return step;
        }
        return null;
    }

    public record Step<T>(T source, int slot, int tier) {}
    private static final class Cursor<T> {
        final T source;
        int slot;
        Cursor(T source) { this.source = source; }
    }
}
