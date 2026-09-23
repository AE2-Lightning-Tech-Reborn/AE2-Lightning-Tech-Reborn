package com.moakiee.ae2lt.recipe.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;

/** Keeps existing nested ItemStack NBT layouts when using the 26.1 codec API. */
public final class LegacyItemStackNbt {
    private LegacyItemStackNbt() {}

    public static CompoundTag save(ItemStack stack, HolderLookup.Provider registries) {
        return (CompoundTag) ItemStack.OPTIONAL_CODEC.encodeStart(
                registries.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
    }

    public static ItemStack parseOptional(HolderLookup.Provider registries, CompoundTag tag) {
        return ItemStack.OPTIONAL_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .result().orElse(ItemStack.EMPTY);
    }
}
