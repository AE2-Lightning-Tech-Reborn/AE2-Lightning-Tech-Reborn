package com.moakiee.ae2lt.crafting.algorithm;

/**
 * Mixed onto AE2 {@code CraftingCalculation} so Tianshu can skip the vanilla
 * fallback after Thunderbolt has already captured a VANILLA-terminal list.
 */
public interface ExclusivePlanningLock {
    void ae2lt$setExclusiveEngine(boolean lock);

    boolean ae2lt$isExclusiveEngine();
}
