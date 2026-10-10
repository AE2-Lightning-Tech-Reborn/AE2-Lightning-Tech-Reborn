package com.moakiee.ae2lt.integration;

/** Shared energy labels for recipe viewers; uses the same rounding and suffixes in both. */
public final class RecipeEnergyFormat {
    private RecipeEnergyFormat() {}

    public static String compactEnergy(long energy) {
        if (energy >= 1_000_000L) {
            return compactValue(energy / 1_000_000D, "m");
        }
        if (energy >= 1_000L) {
            return compactValue(energy / 1_000D, "k");
        }
        return Long.toString(energy);
    }

    private static String compactValue(double value, String suffix) {
        double rounded = Math.round(value * 10.0D) / 10.0D;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001D) {
            return Long.toString(Math.round(rounded)) + suffix;
        }
        return rounded + suffix;
    }
}
