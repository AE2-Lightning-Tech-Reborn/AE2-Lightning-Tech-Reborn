package com.moakiee.ae2lt.crafting.timewheel.allocation;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import org.jetbrains.annotations.Nullable;

/** Forge bridge based on Thunderbolt-Core-Reborn's IBatchCraftingProvider/PreparedBatch
 * admission contract (AE2-Lightning-Tech-Reborn contributors, GNU LGPL 3.0).
 * Preparation must not transfer ownership, mutate or retain the borrowed single-copy inputs.
 * A null result keeps the provider's advisory capacity and ordinary pushBatch behavior. */
public interface TimeWheelBatchAdmission extends IBatchCraftingProvider {
    @Nullable PreparedBatch prepareTimeWheelBatch(IPatternDetails details, KeyCounter[] prototype,
                                                  long maxCraft, BatchJobView job);

    interface PreparedBatch {
        long capacity();

        /** Submit once, returning unaccepted copies. Smaller offers must be revalidated. */
        long push(long copies);
    }
}
