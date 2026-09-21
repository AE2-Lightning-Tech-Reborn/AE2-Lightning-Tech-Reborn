package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.menu.me.crafting.CraftingCPURecord;

@Mixin(value = CraftingCPURecord.class, remap = false)
public interface CraftingCPURecordAccessor {
    @Accessor("cpu")
    ICraftingCPU ae2lt$getCpu();

    @Accessor("size")
    long ae2lt$getSize();

    @Accessor("processors")
    int ae2lt$getProcessors();
}
