package com.moakiee.ae2lt.crafting.algorithm;

import java.util.function.Supplier;

import appeng.api.networking.IGrid;

/** Synchronous router context; asynchronous jobs retain their own captured planning choices. */
public final class ExclusivePlanningContext {
    private static final ThreadLocal<Boolean> EXCLUSIVE = new ThreadLocal<>();

    private ExclusivePlanningContext() {
    }

    public static boolean isExclusiveCalculation() {
        return Boolean.TRUE.equals(EXCLUSIVE.get());
    }

    public static <T> T calculate(IGrid grid, Supplier<T> calculation) {
        var previous = EXCLUSIVE.get();
        EXCLUSIVE.set(ExclusiveCraftingPlanning.locksExclusiveAlgorithm(grid));
        try {
            return calculation.get();
        } finally {
            if (previous == null) {
                EXCLUSIVE.remove();
            } else {
                EXCLUSIVE.set(previous);
            }
        }
    }
}
