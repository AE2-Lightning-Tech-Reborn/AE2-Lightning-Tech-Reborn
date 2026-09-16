package com.moakiee.ae2lt.crafting.report;

import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.crafting.ICraftingPlan;

/** Transfers worker-thread timings to the server-side confirmation menu. */
public final class CraftingReportDiagnostics {
    private static final Map<ICraftingPlan, Long> CALCULATION_NANOS = new WeakHashMap<>();

    private CraftingReportDiagnostics() {
    }

    public static synchronized void record(ICraftingPlan plan, long calculationNanos) {
        CALCULATION_NANOS.put(plan, Math.max(0L, calculationNanos));
    }

    public static synchronized long take(@Nullable ICraftingPlan plan, long fallbackNanos) {
        if (plan == null) {
            return Math.max(0L, fallbackNanos);
        }
        Long measured = CALCULATION_NANOS.remove(plan);
        return measured != null ? measured : Math.max(0L, fallbackNanos);
    }
}
