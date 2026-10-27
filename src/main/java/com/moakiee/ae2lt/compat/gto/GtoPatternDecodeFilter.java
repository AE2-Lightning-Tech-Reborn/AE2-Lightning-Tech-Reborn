package com.moakiee.ae2lt.compat.gto;

import appeng.api.crafting.IPatternDetails;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadedProviderOnlyPatternDetails;

/** Runs after GTO's native decoding, preserving all of its pattern transformations. */
public final class GtoPatternDecodeFilter {
    private GtoPatternDecodeFilter() {}
    public static IPatternDetails ordinaryProvider(IPatternDetails details) {
        return details instanceof OverloadedProviderOnlyPatternDetails ? null : details;
    }
}
