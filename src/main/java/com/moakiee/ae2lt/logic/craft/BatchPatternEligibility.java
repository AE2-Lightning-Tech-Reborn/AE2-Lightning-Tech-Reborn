package com.moakiee.ae2lt.logic.craft;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.function.Predicate;

import appeng.api.crafting.IPatternDetails;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

import com.moakiee.thunderbolt.core.crafting.loop.ClosedLoopBatchPatternDetails;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopExpandedPatternDetails;
import com.moakiee.thunderbolt.core.crafting.pattern.IWrappedPatternDetails;

/**
 * Selects pattern types that may enter Thunderbolt's batch-provider dispatch.
 */
public final class BatchPatternEligibility {
    private BatchPatternEligibility() {
    }

    public static boolean isEligible(IPatternDetails details) {
        return isEligible(
                details,
                candidate -> candidate instanceof ClosedLoopExpandedPatternDetails,
                candidate -> candidate instanceof ClosedLoopBatchPatternDetails);
    }

    static boolean isEligible(
            IPatternDetails details,
            Predicate<IPatternDetails> isClosedLoop,
            Predicate<IPatternDetails> isClosedLoopBatchSafe) {
        java.util.Set<IPatternDetails> visited = null;
        var current = details;

        while (current != null) {
            if (visited != null && !visited.add(current)) return false;
            // Closed-loop execution has stricter accounting requirements than ordinary patterns.
            if (isClosedLoop.test(current)) {
                return isClosedLoopBatchSafe.test(current);
            }
            if (current instanceof IWrappedPatternDetails wrapped) {
                // Ordinary patterns have no wrapper graph to walk or remember.
                if (visited == null) {
                    visited = Collections.newSetFromMap(new IdentityHashMap<>());
                    visited.add(current);
                }
                current = wrapped.wrappedPatternDetails();
                continue;
            }
            return current instanceof IMolecularAssemblerSupportedPattern
                    || current.supportsPushInputsToExternalInventory();
        }
        return false;
    }
}
