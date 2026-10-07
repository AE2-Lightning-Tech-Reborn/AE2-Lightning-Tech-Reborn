package com.moakiee.ae2lt.crafting.report;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

/** Menu-owned stock snapshot. A replan can only subtract confirmed extraction deficits. */
public final class CraftingReportInventory {
    private static final ThreadLocal<CraftingReportInventory> CAPTURE = new ThreadLocal<>();

    @Nullable
    private KeyCounter stock;
    private final KeyCounter missing = new KeyCounter();

    public boolean isCaptured() {
        return stock != null;
    }

    public void recordMissing(GenericStack deficit) {
        if (deficit.amount() > 0) {
            // Retrying the same plan must not count the same shortfall twice.
            missing.set(deficit.what(), Math.max(missing.get(deficit.what()), deficit.amount()));
        }
    }

    public void subtractMissing() {
        if (stock == null) {
            throw new IllegalStateException("No crafting inventory snapshot was captured");
        }
        for (var entry : missing) {
            long available = Math.max(0L, stock.get(entry.getKey()));
            stock.set(entry.getKey(), available - Math.min(available, entry.getLongValue()));
        }
        stock.removeZeros();
        missing.clear();
    }

    /** AE2 constructs its network simulation state synchronously before submitting the worker. */
    public <T> T captureCalculation(Supplier<T> startCalculation) {
        var previous = CAPTURE.get();
        CAPTURE.set(this);
        try {
            return startCalculation.get();
        } finally {
            if (previous == null) {
                CAPTURE.remove();
            } else {
                CAPTURE.set(previous);
            }
        }
    }

    /** Called at the two native snapshot-read sites; a replan never invokes the live supplier. */
    public static KeyCounter readAvailableStacks(Supplier<KeyCounter> liveInventory) {
        var capture = CAPTURE.get();
        if (capture == null) {
            return liveInventory.get();
        }
        if (capture.stock == null) {
            capture.stock = copy(liveInventory.get());
        }
        // The calculation gets its own copy, leaving the retained snapshot immutable to workers.
        return copy(capture.stock);
    }

    private static KeyCounter copy(KeyCounter source) {
        var result = new KeyCounter();
        for (var entry : source) {
            if (entry.getLongValue() > 0) {
                result.set(entry.getKey(), entry.getLongValue());
            }
        }
        return result;
    }
}
