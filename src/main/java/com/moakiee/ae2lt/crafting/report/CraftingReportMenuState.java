package com.moakiee.ae2lt.crafting.report;

/** Client-synchronized diagnostics for AE2LT's crafting confirmation report. */
public interface CraftingReportMenuState {
    boolean ae2lt$shouldShowReport();

    long ae2lt$getCalculationNanos();
}
