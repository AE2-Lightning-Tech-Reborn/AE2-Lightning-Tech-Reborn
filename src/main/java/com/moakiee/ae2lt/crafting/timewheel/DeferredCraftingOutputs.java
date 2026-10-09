package com.moakiee.ae2lt.crafting.timewheel;

import java.util.function.ObjLongConsumer;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.crafting.runtime.api.DeferredCraftingProvider;

/** One dispatch's physical returns. Closing the sink before draining prevents reentrant enqueue. */
final class DeferredCraftingOutputs implements DeferredCraftingProvider.OutputSink {
    private AEKey singleKey;
    private long singleAmount;
    private KeyCounter pending;
    private boolean closed;

    @Override
    public boolean enqueue(KeyCounter outputs) {
        if (closed) return false;
        for (var entry : outputs) {
            if (entry.getLongValue() < 0
                    || get(entry.getKey()) > Long.MAX_VALUE - entry.getLongValue()) return false;
        }
        for (var entry : outputs) {
            if (entry.getLongValue() > 0) add(entry.getKey(), entry.getLongValue());
        }
        return true;
    }

    @Override
    public boolean enqueue(AEKey key, long amount) {
        if (closed || amount < 0 || get(key) > Long.MAX_VALUE - amount) return false;
        if (amount > 0) add(key, amount);
        return true;
    }

    private long get(AEKey key) {
        return pending != null ? pending.get(key) : key.equals(singleKey) ? singleAmount : 0;
    }

    private void add(AEKey key, long amount) {
        if (pending == null) {
            if (singleKey == null) { singleKey = key; singleAmount = amount; return; }
            if (singleKey.equals(key)) { singleAmount += amount; return; }
            pending = new KeyCounter();
            pending.add(singleKey, singleAmount);
            singleKey = null; singleAmount = 0;
        }
        pending.add(key, amount);
    }

    void drain(ObjLongConsumer<AEKey> receiver, ObjLongConsumer<AEKey> fallback) {
        closed = true;
        if (pending == null) {
            var key = singleKey;
            long amount = singleAmount;
            // Clear ownership before the callback, including an exceptional or reentrant return.
            singleKey = null; singleAmount = 0;
            if (amount > 0) receiver.accept(key, amount);
            return;
        }
        try {
            for (var entry : pending) {
                long amount = entry.getLongValue();
                // Ownership passes to the receiver, which must retain any unaccepted remainder.
                entry.setValue(0L);
                if (amount > 0) receiver.accept(entry.getKey(), amount);
            }
        } finally {
            // A failed callback must not strand later outputs in a stack-local queue.
            for (var entry : pending) {
                if (entry.getLongValue() > 0) fallback.accept(entry.getKey(), entry.getLongValue());
            }
            pending.clear();
        }
    }
}
