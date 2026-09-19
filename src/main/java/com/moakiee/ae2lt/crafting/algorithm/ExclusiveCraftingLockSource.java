package com.moakiee.ae2lt.crafting.algorithm;

import net.minecraft.resources.ResourceLocation;

import com.moakiee.thunderbolt.api.crafting.CraftingAlgorithmSelection;

/**
 * Grid-side exclusive planning lock that is not a Tianshu port. GTL's Transfinite
 * ME interface mixin implements this so a GTL-only network can lock V2 / CP-SAT /
 * vanilla without {@code IGrid#getMachines} on a GTL class.
 */
public interface ExclusiveCraftingLockSource {
    boolean ae2lt$isExclusiveLockActive();

    ResourceLocation ae2lt$getExclusiveAlgorithm();

    int ae2lt$getLockCpuPriority();

    int ae2lt$getLockProviderPriority();

    void ae2lt$cycleExclusiveAlgorithm();

    void ae2lt$setExclusiveSelection(CraftingAlgorithmSelection selection);
}
