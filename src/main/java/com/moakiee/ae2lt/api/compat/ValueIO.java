package com.moakiee.ae2lt.api.compat;

import com.moakiee.ae2lt.recipe.compat.LegacyValueIo;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Public bridge for add-ons retaining their existing flat NBT layout on 26.1. */
public final class ValueIO {
    private ValueIO() {}

    public static CompoundTag readableTag(ValueInput input) {
        return LegacyValueIo.readableTag(input);
    }

    public static CompoundTag writableTag(ValueOutput output) {
        return LegacyValueIo.writableTag(output);
    }

    public static ValueInput input(CompoundTag tag, HolderLookup.Provider registries) {
        return LegacyValueIo.input(tag, registries);
    }

    public static ValueOutput output(CompoundTag tag, HolderLookup.Provider registries) {
        return LegacyValueIo.output(tag, registries);
    }
}
