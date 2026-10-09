package com.moakiee.ae2lt.crafting.report;

import appeng.menu.me.crafting.CraftingPlanSummary;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReports;

public final class CraftingReportStartState {
    private CraftingReportStartState() {
    }

    public static boolean normallyStartable(CraftingPlanSummary plan, boolean bigMode) {
        return plan != null && !plan.isSimulation() && (ExactPlanReports.get(plan) == null || bigMode);
    }

    public static boolean forceCandidate(CraftingPlanSummary plan, boolean bigMode) {
        return plan != null && plan.isSimulation() && !bigMode && ExactPlanReports.get(plan) == null
                && plan.getEntries().stream().anyMatch(entry -> entry.getMissingAmount() > 0);
    }
}
