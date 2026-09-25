package com.moakiee.ae2lt.logic;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import net.minecraft.nbt.ListTag;

/** Owned, bounded input staging; never advertised as an additional ME inventory. */
public final class BufferedInterfaceInput {
    public static final int FLUSH_INTERVAL = 5;
    public static final int MAX_KEYS = 1024;
    public static final int FLUSH_MAX_KEYS = 128;
    // Same quantity budget as the interface's 36 x 1 KiB configuration slots,
    // shared by all buffered keys of each resource type rather than per key.
    public static final long CAPACITY_BYTES = 36L * 1024;

    private final LinkedHashMap<AEKey, Long> entries = new LinkedHashMap<>();
    private final Map<AEKeyType, Long> typeAmounts = new IdentityHashMap<>();
    private long lastFlushTick = Long.MIN_VALUE;
    private boolean flushing;

    public static long capacity(AEKeyType type) {
        long perByte = Math.max(1, type.getAmountPerByte());
        return perByte > Long.MAX_VALUE / CAPACITY_BYTES
                ? Long.MAX_VALUE : perByte * CAPACITY_BYTES;
    }

    /** SIMULATE reads only local capacity. In particular it does not reserve space. */
    public long insert(AEKey key, long requested, Actionable mode) {
        if (flushing || key == null || requested <= 0) return 0;
        long previous = entries.getOrDefault(key, 0L);
        if (previous == 0 && entries.size() >= MAX_KEYS) return 0;
        long typeAmount = typeAmounts.getOrDefault(key.getType(), 0L);
        long available = Math.max(0, capacity(key.getType()) - typeAmount);
        long accepted = Math.min(requested, Math.min(available, Long.MAX_VALUE - previous));
        if (accepted > 0 && mode == Actionable.MODULATE) {
            entries.put(key, previous + accepted);
            typeAmounts.put(key.getType(), typeAmount + accepted);
        }
        return accepted;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public long amount(AEKey key) {
        return entries.getOrDefault(key, 0L);
    }

    public boolean isFlushDue(long now, int phase) {
        return !flushing && !entries.isEmpty()
                && Math.floorMod(now, FLUSH_INTERVAL) == Math.floorMod(phase, FLUSH_INTERVAL)
                && (lastFlushTick == Long.MIN_VALUE || now < lastFlushTick
                        || now - lastFlushTick >= FLUSH_INTERVAL);
    }

    /** One bounded pass per five ticks. Rejected/partial keys rotate behind unvisited keys. */
    public void flush(MEStorage network, IActionSource source, long now, int phase, Runnable changed) {
        if (!isFlushDue(now, phase)) return;
        lastFlushTick = now;
        flushing = true;
        boolean mutated = false;
        try {
            int attempts = Math.min(FLUSH_MAX_KEYS, entries.size());
            for (int i = 0; i < attempts; i++) {
                var entry = entries.entrySet().iterator().next();
                var key = entry.getKey();
                long amount = entry.getValue();
                // Flush is the sole network insertion. No network SIMULATE beforehand.
                long inserted = network.insert(key, amount, Actionable.MODULATE, source);
                if (inserted < 0 || inserted > amount) {
                    throw new IllegalStateException("Storage returned invalid inserted amount: " + inserted);
                }
                entries.remove(key);
                if (inserted < amount) entries.put(key, amount - inserted);
                if (inserted > 0) {
                    long remaining = typeAmounts.get(key.getType()) - inserted;
                    if (remaining == 0) typeAmounts.remove(key.getType());
                    else typeAmounts.put(key.getType(), remaining);
                    mutated = true;
                }
            }
        } finally {
            flushing = false;
            if (mutated) changed.run();
        }
    }

    public ListTag write() {
        var result = new ListTag();
        entries.forEach((key, amount) -> result.add(
                GenericStack.writeTag(new GenericStack(key, amount))));
        return result;
    }

    public void read(ListTag saved) {
        clear();
        for (int i = 0; i < saved.size(); i++) {
            var stack = GenericStack.readTag(saved.getCompound(i));
            if (stack == null || stack.amount() <= 0) continue;
            // Loading must retain existing ownership, even if limits are lowered later.
            entries.merge(stack.what(), stack.amount(), Math::addExact);
            typeAmounts.merge(stack.what().getType(), stack.amount(), Math::addExact);
        }
    }

    public void clear() {
        entries.clear();
        typeAmounts.clear();
        lastFlushTick = Long.MIN_VALUE;
    }
}
