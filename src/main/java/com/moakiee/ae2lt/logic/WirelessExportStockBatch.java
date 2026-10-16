package com.moakiee.ae2lt.logic;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.function.BiConsumer;

/** A server-thread transfer scope; unused prefetched stock returns to persistent custody on close. */
public final class WirelessExportStockBatch implements MEStorage, AutoCloseable {
    private final MEStorage network;
    private final int targets;
    private final BiConsumer<AEKey, Long> remainder;
    private final Object2LongOpenHashMap<AEKey> stock = new Object2LongOpenHashMap<>();

    public WirelessExportStockBatch(MEStorage network, int targets, BiConsumer<AEKey, Long> remainder) {
        this.network = network;
        this.targets = Math.max(1, Math.min(64, targets));
        this.remainder = remainder;
    }

    @Override public net.minecraft.network.chat.Component getDescription() { return network.getDescription(); }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (key == null || amount <= 0) return 0;
        long held = stock.getLong(key);
        if (mode == Actionable.SIMULATE) {
            if (held >= amount) return amount;
            return held + network.extract(key, amount - held, mode, source);
        }
        if (held < amount) {
            long request = amount > Long.MAX_VALUE / targets ? Long.MAX_VALUE : amount * targets;
            long fetched = network.extract(key, request - held, mode, source);
            held += fetched;
            stock.put(key, held);
        }
        long extracted = Math.min(amount, held);
        stock.put(key, held - extracted);
        return extracted;
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (var entry : stock.object2LongEntrySet()) {
            if (entry.getLongValue() <= 0) continue;
            try { remainder.accept(entry.getKey(), entry.getLongValue()); }
            catch (RuntimeException error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        stock.clear();
        if (failure != null) throw failure;
    }
}
