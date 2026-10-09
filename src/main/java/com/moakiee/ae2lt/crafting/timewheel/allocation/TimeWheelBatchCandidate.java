package com.moakiee.ae2lt.crafting.timewheel.allocation;

import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;

/** Read-only view of the native dispatcher's already selected candidates. */
public interface TimeWheelBatchCandidate {
    IBatchCraftingProvider ae2lt$provider();
    long ae2lt$capacity();
}
