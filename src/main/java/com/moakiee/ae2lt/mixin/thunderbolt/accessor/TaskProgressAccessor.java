package com.moakiee.ae2lt.mixin.thunderbolt.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Remaining operations of one task of AE2's executing crafting job (not the TimeWheel job model). */
@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress", remap = false)
public interface TaskProgressAccessor {
    @Accessor("value")
    long ae2lt$getValue();
}
