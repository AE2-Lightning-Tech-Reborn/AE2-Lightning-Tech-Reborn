package com.moakiee.ae2lt.crafting.runtime;

import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.crafting.runtime.api.DeferredCraftingProvider;
import com.moakiee.thunderbolt.core.storage.big.BigAmounts;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.ObjLongConsumer;

/**
 * One backend-owned accounting transaction. Physical returns are owned here until accounting
 * finishes. An aborted transaction retains them without advancing the job. Closing seals both
 * input paths before callbacks, and never delivers twice. The receiver must retain what it takes
 * before invoking callbacks; fallback owns any outputs not yet handed to a receiver.
 *
 * <p>Only the return boundary knows both amount types. The ordinary path keeps primitive longs;
 * no CPU inventory, plan cursor, provider ABI or per-copy arithmetic is generalized.
 */
public final class CraftingDispatch implements AutoCloseable {
    private ObjLongConsumer<AEKey> receiver;
    private ObjLongConsumer<AEKey> fallback;
    private BiConsumer<AEKey, BigInteger> bigReceiver;
    private BiConsumer<AEKey, BigInteger> bigFallback;
    private DeferredCraftingOutputs outputs;
    private Map<AEKey, BigInteger> bigOutputs;
    private boolean accounted;
    private boolean closed;

    public CraftingDispatch(ObjLongConsumer<AEKey> receiver, ObjLongConsumer<AEKey> fallback) {
        this.receiver = receiver;
        this.fallback = fallback;
    }

    public static CraftingDispatch exact(BiConsumer<AEKey, BigInteger> receiver,
                                        BiConsumer<AEKey, BigInteger> fallback) {
        var transaction = new CraftingDispatch(null, null);
        transaction.bigReceiver = receiver;
        transaction.bigFallback = fallback;
        return transaction;
    }

    public DeferredCraftingProvider.OutputSink outputSink() {
        if (closed || receiver == null) throw new IllegalStateException("No active long return path");
        if (outputs == null) outputs = new DeferredCraftingOutputs();
        return outputs;
    }

    public BigInteger staged(AEKey key) {
        return bigOutputs == null ? BigInteger.ZERO : bigOutputs.getOrDefault(key, BigInteger.ZERO);
    }

    /** The caller has validated its waiting ledger and transfers only actual, accepted output. */
    public boolean enqueueExact(AEKey key, BigInteger amount) {
        if (closed || bigReceiver == null) return false;
        BigAmounts.nonNegative(amount);
        if (amount.signum() == 0) return true;
        var total = BigAmounts.nonNegative(staged(key).add(amount));
        if (bigOutputs == null) bigOutputs = new LinkedHashMap<>();
        bigOutputs.put(key, total);
        return true;
    }

    /** Call only after acceptance/refunds, waiting quantities and tick usage have been committed. */
    public void accounted() {
        if (closed) throw new IllegalStateException("Dispatch already closed");
        accounted = true;
    }

    public boolean closed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            if (outputs != null) outputs.drain(accounted ? receiver : fallback, fallback);
            if (bigOutputs != null) {
                var delivery = accounted ? bigReceiver : bigFallback;
                try {
                    for (var entry : bigOutputs.entrySet()) {
                        var amount = entry.getValue();
                        entry.setValue(BigInteger.ZERO);
                        if (amount.signum() > 0) delivery.accept(entry.getKey(), amount);
                    }
                } finally {
                    for (var entry : bigOutputs.entrySet())
                        if (entry.getValue().signum() > 0) bigFallback.accept(entry.getKey(), entry.getValue());
                }
            }
        } finally {
            // Async machine tokens may outlive this call. Do not retain an obsolete CPU/job.
            outputs = null;
            bigOutputs = null;
            receiver = null;
            fallback = null;
            bigReceiver = null;
            bigFallback = null;
        }
    }
}
