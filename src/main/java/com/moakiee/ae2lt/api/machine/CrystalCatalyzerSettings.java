package com.moakiee.ae2lt.api.machine;

/** Processing policy supplied by specialized crystal-catalyzer block entities. */
public record CrystalCatalyzerSettings(boolean standalone, boolean waterOnly,
        int minimumCatalysts, int catalystSlotLimit, int outputSlotLimit,
        int fixedProcessTicks, boolean energyFree, boolean scaleOutput, boolean modeSwitching) {
    public static final CrystalCatalyzerSettings DEFAULT =
            new CrystalCatalyzerSettings(false, false, 0, 256, 16384, 0, false, true, true);

    public CrystalCatalyzerSettings {
        if (minimumCatalysts < 0 || catalystSlotLimit < 1 || outputSlotLimit < 1 || fixedProcessTicks < 0)
            throw new IllegalArgumentException("Invalid catalyzer limits");
        if (minimumCatalysts > catalystSlotLimit) throw new IllegalArgumentException("Catalysts cannot fit in slot");
    }
}
