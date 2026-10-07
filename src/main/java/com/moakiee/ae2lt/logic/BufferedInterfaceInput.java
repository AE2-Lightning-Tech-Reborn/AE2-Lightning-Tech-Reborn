package com.moakiee.ae2lt.logic;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import net.minecraft.nbt.ListTag;

public final class BufferedInterfaceInput {
    public static final int FLUSH_INTERVAL = 5;
    public static final int MAX_KEYS = 1024;
    public static final int FLUSH_MAX_KEYS = 128;
    public static final long CAPACITY_BYTES = 36L * 1024;

    private final Object2LongLinkedOpenHashMap<AEKey> entries = new Object2LongLinkedOpenHashMap<>();
    private final Reference2LongOpenHashMap<AEKeyType> typeAmounts = new Reference2LongOpenHashMap<>();
    private long lastFlushTick = Long.MIN_VALUE;
    private boolean flushing;

    public static long capacity(AEKeyType type) {
        long perByte = Math.max(1, type.getAmountPerByte());
        return perByte > Long.MAX_VALUE / CAPACITY_BYTES
                ? Long.MAX_VALUE : perByte * CAPACITY_BYTES;
    }

    public long insert(AEKey key, long requested, Actionable mode) {
        if (flushing || key == null || requested <= 0) return 0;
        long previous = entries.getLong(key);
        if (previous == 0 && entries.size() >= MAX_KEYS) return 0;
        long typeAmount = typeAmounts.getLong(key.getType());
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
        return entries.getLong(key);
    }

    public boolean isFlushDue(long now, int phase) {
        return !flushing && !entries.isEmpty()
                && Math.floorMod(now, FLUSH_INTERVAL) == Math.floorMod(phase, FLUSH_INTERVAL)
                && (lastFlushTick == Long.MIN_VALUE || now < lastFlushTick
                        || now - lastFlushTick >= FLUSH_INTERVAL);
    }

    public void flush(MEStorage network, IActionSource source, long now, int phase, Runnable changed) {
        if (!isFlushDue(now, phase)) return;
        lastFlushTick = now;
        flushing = true;
        boolean mutated = false;
        try {
            int attempts = Math.min(FLUSH_MAX_KEYS, entries.size());
            for (int index = 0; index < attempts; index++) {
                var key = entries.firstKey();
                long amount = entries.getLong(key);
                long inserted = network.insert(key, amount, Actionable.MODULATE, source);
                if (inserted < 0 || inserted > amount) {
                    throw new IllegalStateException("Storage returned invalid inserted amount: " + inserted);
                }
                if (inserted == amount) entries.removeFirstLong();
                else entries.putAndMoveToLast(key, amount - inserted);
                if (inserted > 0) {
                    long remaining = typeAmounts.getLong(key.getType()) - inserted;
                    if (remaining == 0) typeAmounts.removeLong(key.getType());
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
        for (int index = 0; index < saved.size(); index++) {
            var stack = GenericStack.readTag(saved.getCompound(index));
            if (stack == null || stack.amount() <= 0) continue;
            entries.put(stack.what(), Math.addExact(entries.getLong(stack.what()), stack.amount()));
            var type = stack.what().getType();
            typeAmounts.put(type, Math.addExact(typeAmounts.getLong(type), stack.amount()));
        }
    }

    public void clear() {
        entries.clear();
        typeAmounts.clear();
        lastFlushTick = Long.MIN_VALUE;
    }
}
