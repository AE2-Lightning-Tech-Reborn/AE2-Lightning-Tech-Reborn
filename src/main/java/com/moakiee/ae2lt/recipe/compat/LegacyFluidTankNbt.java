package com.moakiee.ae2lt.recipe.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Preserves the prior tank's nested "Fluid" tag with NeoForge ValueIO. */
public final class LegacyFluidTankNbt {
    private LegacyFluidTankNbt() {}

    public static CompoundTag save(FluidTank tank, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tank.serialize(LegacyValueIo.output(tag, registries));
        return tag;
    }

    public static void load(FluidTank tank, HolderLookup.Provider registries, CompoundTag tag) {
        tank.deserialize(LegacyValueIo.input(tag, registries));
    }
}
