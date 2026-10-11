package com.moakiee.ae2lt.integration.eaep;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.crafting.big.BigCraftingPlan;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReports;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class EaepForcedCraftingPlanAccess {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String WRAPPER = "com.extendedae_plus.crafting.ForcedCraftingPlan";

    private EaepForcedCraftingPlanAccess() {
    }

    public static Data read(ICraftingPlan plan) {
        if (!plan.getClass().getName().equals(WRAPPER)) {
            return new Data(plan, new KeyCounter());
        }
        try {
            return readWrapper(plan);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            LOG.warn("Cannot safely read the forced crafting plan", failure);
            return null;
        }
    }

    static Data readWrapper(ICraftingPlan plan) throws ReflectiveOperationException {
        var field = plan.getClass().getDeclaredField("delegate");
        field.setAccessible(true);
        var original = (ICraftingPlan) field.get(plan);
        if (original == null || original instanceof BigCraftingPlan || ExactPlanReports.isPreview(original)) {
            throw new IllegalArgumentException("Exact plans cannot use long-amount force crafting");
        }
        var missing = (KeyCounter) plan.getClass().getMethod("eap$getManualMissingItems").invoke(plan);
        var snapshot = new KeyCounter();
        for (var entry : missing) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                snapshot.add(entry.getKey(), entry.getLongValue());
            }
        }
        return new Data(original, snapshot);
    }

    public record Data(ICraftingPlan original, KeyCounter manualMissing) {
    }
}
