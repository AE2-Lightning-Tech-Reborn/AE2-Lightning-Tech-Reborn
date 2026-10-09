package com.moakiee.ae2lt.mixin.thunderbolt.accessor;

import com.moakiee.ae2lt.crafting.timewheel.allocation.TimeWheelBatchCandidate;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.moakiee.thunderbolt.core.crafting.batch.BatchExecutor$EligibleProvider", remap = false)
public interface BatchCandidateAccessor extends TimeWheelBatchCandidate {
    @Override @Accessor("provider") IBatchCraftingProvider ae2lt$provider();
    @Override @Accessor("capacity") long ae2lt$capacity();
}
