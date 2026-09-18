package com.moakiee.ae2lt.crafting.timewheel.allocation;

import appeng.api.crafting.IPatternDetails;
import com.moakiee.thunderbolt.core.crafting.pattern.PlannedInputPattern;

/** Only removes concrete input bindings; execution wrappers and loop ownership remain intact. */
public final class ExecutionTaskInputs {
    private ExecutionTaskInputs() { }

    public static IPatternDetails unbound(IPatternDetails pattern) {
        while (pattern instanceof PlannedInputPattern planned) {
            pattern = planned.providerLookupPattern();
        }
        return pattern;
    }
}
