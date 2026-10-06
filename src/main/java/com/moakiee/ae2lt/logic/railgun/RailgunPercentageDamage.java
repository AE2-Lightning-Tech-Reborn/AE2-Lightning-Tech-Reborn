package com.moakiee.ae2lt.logic.railgun;

import com.moakiee.ae2lt.item.railgun.RailgunChargeTier;
import com.moakiee.ae2lt.item.railgun.RailgunExecutionMode;

/** Charged Overload damage only; the ordinary damage event owns health and death. */
public final class RailgunPercentageDamage {
    private RailgunPercentageDamage() {}

    public static boolean eligible(RailgunChargeTier tier, RailgunExecutionMode mode,
                                   boolean overload, boolean multidimensional, boolean chainPropagation) {
        return tier != RailgunChargeTier.HV && mode == RailgunExecutionMode.PERCENTAGE
                && overload && !multidimensional && !chainPropagation;
    }

    public static double bonus(double maxHealth, double fraction) {
        if (!Double.isFinite(maxHealth) || maxHealth <= 0 || !Double.isFinite(fraction) || fraction <= 0) return 0;
        return Math.min(Float.MAX_VALUE, maxHealth * Math.min(1.0D, fraction));
    }
}
