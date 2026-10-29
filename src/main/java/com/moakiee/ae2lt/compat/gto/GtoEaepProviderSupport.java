package com.moakiee.ae2lt.compat.gto;

import java.util.List;
import java.util.Set;
import java.util.Collections;
import appeng.api.stacks.AEKey;
import appeng.api.crafting.IPatternDetails;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.me.cluster.implementations.CraftingCPUCluster;

/** Optional EAEP types stay out of descriptors so the base installation remains independent. */
public final class GtoEaepProviderSupport {
    private GtoEaepProviderSupport() { }

    public static boolean contains(List<?> patterns, Object pattern) {
        if (pattern != null && (pattern.getClass().getName().equals("com.extendedae_plus.api.crafting.ScaledProcessingPattern")
                || pattern.getClass().getName().equals("com.extendedae_plus.api.crafting.ScaledMolecularAssemblerPattern"))) {
            try {
                var original = pattern.getClass().getMethod("getOriginal").invoke(pattern);
                if (original != null && patterns.contains(original)) return true;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("EAEP scaled pattern API changed", failure);
            }
        }
        return patterns.contains(pattern);
    }

    public static void tryVirtualCompletion(PatternProviderLogic provider, IPatternDetails pattern) {
        try {
            if (!(boolean) provider.getClass().getMethod("eap$compatIsVirtualCraftingEnabled").invoke(provider)) return;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("EAEP virtual crafting bridge missing", failure);
        }
        var grid = provider.getGrid();
        if (grid == null) return;
        for (var cpu : grid.getCraftingService().getCpus()) {
            if (cpu instanceof CraftingCPUCluster cluster && cpu.isBusy()
                    && GtoEaepVirtualCpu.completeLastTask(cluster.craftingLogic, pattern)) return;
        }
    }

    public static boolean containsBlockingInput(PatternProviderTarget target, Set<AEKey> allInputs,
            PatternProviderLogic provider, IPatternDetails pattern) {
        try {
            if (provider.isBlocking() && (boolean) provider.getClass().getMethod("eap$getAdvancedBlocking").invoke(provider)) {
                boolean complete = true;
                for (var input : pattern.getInputs()) {
                    boolean matched = false;
                    for (var candidate : input.getPossibleInputs()) {
                        if (target.containsPatternInput(Collections.singleton(candidate.what().dropSecondary()))) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) { complete = false; break; }
                }
                if (complete) return false;
            }
            return target.containsPatternInput(allInputs);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("EAEP advanced blocking API changed", failure);
        }
    }
}
