package com.moakiee.ae2lt.crafting.report;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftingPlanSummary;

import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReports;

public final class MissingMaterialBookmarks {
    private MissingMaterialBookmarks() {
    }

    public static boolean hasMissing(CraftingPlanSummary plan) {
        return missingKeys(plan).anyMatch(MissingMaterialBookmarks::isSupported);
    }

    public static List<AEKey> keys(CraftingPlanSummary plan) {
        return missingKeys(plan)
                .filter(MissingMaterialBookmarks::isSupported)
                .distinct()
                .toList();
    }

    private static boolean isSupported(AEKey key) {
        return key instanceof AEItemKey || key instanceof AEFluidKey;
    }

    private static Stream<AEKey> missingKeys(CraftingPlanSummary plan) {
        if (plan == null) {
            return Stream.empty();
        }
        var exact = ExactPlanReports.get(plan);
        if (exact != null) {
            return exact.entries().entrySet().stream()
                    .filter(entry -> entry.getValue().missing().signum() > 0)
                    .map(Map.Entry::getKey);
        }
        return plan.getEntries().stream()
                .filter(entry -> entry.getMissingAmount() > 0)
                .map(entry -> entry.getWhat());
    }
}
