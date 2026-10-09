package com.moakiee.ae2lt.logic.craft.migration;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;
import java.util.function.LongSupplier;

/** Server-thread leases and a fair, shared cooperative budget across dimensions. */
public final class PatternMigrationBudget {
    public static final long GRID_NANOS = 2_000_000L;
    public static final long SERVER_NANOS = 4_000_000L;
    public static final int MAX_SLOTS = 256;
    public static final int MAX_DECODES = 64;
    private static final Map<Object, PatternMigrationBudget> SERVERS = new WeakHashMap<>();
    private final Map<Object, WeakReference<Object>> owners = new WeakHashMap<>();
    private final ArrayDeque<WeakReference<Object>> rotation = new ArrayDeque<>();
    private final ArrayDeque<WeakReference<Object>> grants = new ArrayDeque<>();
    private final Set<Object> used = Collections.newSetFromMap(new WeakHashMap<>());
    private final LongSupplier clock;
    private long tick = Long.MIN_VALUE;
    private long spent;

    public PatternMigrationBudget(LongSupplier clock) { this.clock = clock; }

    public static PatternMigrationBudget forServer(Object server) {
        return SERVERS.computeIfAbsent(server, ignored -> new PatternMigrationBudget(System::nanoTime));
    }

    public boolean acquire(Object grid, Object owner) {
        var existing = owners.get(grid);
        if (existing != null && existing.get() != null) return existing.get() == owner;
        owners.put(grid, new WeakReference<>(owner));
        rotation.addLast(new WeakReference<>(owner));
        return true;
    }

    public void release(Object grid, Object owner) {
        var existing = owners.get(grid);
        if (existing != null && existing.get() == owner) owners.remove(grid);
        rotation.removeIf(ref -> ref.get() == null || ref.get() == owner);
        grants.removeIf(ref -> ref.get() == null || ref.get() == owner);
    }

    public boolean isOwner(Object grid, Object owner) {
        var existing = owners.get(grid);
        return existing != null && existing.get() == owner;
    }

    public long begin(long serverTick, Object owner) {
        if (tick != serverTick) {
            tick = serverTick;
            spent = 0;
            used.clear();
            grants.clear();
            rotation.removeIf(ref -> ref.get() == null);
            int count = Math.min(2, rotation.size());
            for (int i = 0; i < count; i++) {
                var ref = rotation.removeFirst();
                grants.addLast(ref);
                rotation.addLast(ref);
            }
        }
        if (spent >= SERVER_NANOS || used.contains(owner)
                || grants.stream().noneMatch(ref -> ref.get() == owner)) return 0;
        used.add(owner);
        return clock.getAsLong() + Math.min(GRID_NANOS, SERVER_NANOS - spent);
    }

    public void record(long nanos) { spent += Math.max(0, nanos); }
}
