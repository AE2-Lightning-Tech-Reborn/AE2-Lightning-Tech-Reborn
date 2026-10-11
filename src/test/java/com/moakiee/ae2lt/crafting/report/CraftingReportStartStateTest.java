package com.moakiee.ae2lt.crafting.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReport;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReports;
import org.junit.jupiter.api.Test;

class CraftingReportStartStateTest {
    @Test
    void missingOrdinaryPlanCanOnlyUseForceStart() {
        var plan = plan(true, 5);
        assertFalse(CraftingReportStartState.normallyStartable(plan, false));
        assertTrue(CraftingReportStartState.forceCandidate(plan, false));
    }

    @Test
    void ordinaryExecutablePlanUsesNormalStartEvenWhileHoldingShift() {
        var plan = plan(false, 0);
        assertTrue(CraftingReportStartState.normallyStartable(plan, false));
        assertFalse(CraftingReportStartState.forceCandidate(plan, false));
    }

    @Test
    void pendingAndUnresolvedPlansCannotBeForced() {
        assertFalse(CraftingReportStartState.normallyStartable(null, false));
        assertFalse(CraftingReportStartState.forceCandidate(null, false));
        assertFalse(CraftingReportStartState.forceCandidate(new CraftingPlanSummary(0, true, List.of()), false));
        assertFalse(CraftingReportStartState.forceCandidate(plan(true, 0), false));
    }

    @Test
    void exactPreviewCannotBypassExecutionLimits() {
        var plan = plan(true, Long.MAX_VALUE);
        ExactPlanReports.attach(plan, new ExactPlanReport(BigInteger.ZERO, Map.of(), false));
        assertFalse(CraftingReportStartState.normallyStartable(plan, false));
        assertFalse(CraftingReportStartState.forceCandidate(plan, false));
    }

    @Test
    void bigModeNeverUsesTheLongAmountForceStartPath() {
        assertFalse(CraftingReportStartState.forceCandidate(plan(true, 5), true));
        var executable = plan(false, 0);
        ExactPlanReports.attach(executable, new ExactPlanReport(BigInteger.ZERO, Map.of(), false));
        assertTrue(CraftingReportStartState.normallyStartable(executable, true));
        assertFalse(CraftingReportStartState.normallyStartable(executable, false));
    }

    private static CraftingPlanSummary plan(boolean simulation, long missing) {
        return new CraftingPlanSummary(0, simulation, List.of(
                new CraftingPlanSummaryEntry(LightningKey.HIGH_VOLTAGE, missing, 0, 0)));
    }
}
