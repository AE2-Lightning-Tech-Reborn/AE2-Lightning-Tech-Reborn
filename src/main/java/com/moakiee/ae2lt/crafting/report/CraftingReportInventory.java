package com.moakiee.ae2lt.crafting.report;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

/** Menu-owned stock snapshot. A replan can only lower stock to observed extractable amounts. */
public final class CraftingReportInventory {
    private static final ThreadLocal<CraftingReportInventory> CAPTURE = new ThreadLocal<>();

    @Nullable
    private KeyCounter stock;
    // Keep zero observations: an exhausted key must be removed on the next replan.
    private final Map<AEKey, Long> availableLimits = new HashMap<>();

    public boolean isCaptured() {
        return stock != null;
    }

    public void recordMissing(GenericStack deficit, MEStorage inventory, IActionSource source) {
        if (deficit.amount() > 0) {
            // Submission has already rolled its partial extraction back. Probe only the failed key,
            // using the same source, without taking items or reading a new whole-network snapshot.
            long available = Math.max(0L,
                    inventory.extract(deficit.what(), Long.MAX_VALUE, Actionable.SIMULATE, source));
            availableLimits.merge(deficit.what(), available, Math::min);
        }
    }

    public void clampToAvailable() {
        if (stock == null) {
            throw new IllegalStateException("No crafting inventory snapshot was captured");
        }
        for (var entry : availableLimits.entrySet()) {
            long available = Math.max(0L, stock.get(entry.getKey()));
            stock.set(entry.getKey(), Math.min(available, entry.getValue()));
        }
        stock.removeZeros();
        availableLimits.clear();
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

    /** Called at the two native snapshot-read sites; a replan never rereads the full inventory. */
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
