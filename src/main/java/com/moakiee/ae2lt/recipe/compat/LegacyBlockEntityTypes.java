package com.moakiee.ae2lt.recipe.compat;

import java.util.Set;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Adapts the removed BlockEntityType.Builder without changing registrations. */
public final class LegacyBlockEntityTypes {
    private LegacyBlockEntityTypes() {
    }

    @SafeVarargs
    public static <T extends BlockEntity> BlockEntityType<T> of(
            BlockEntityType.BlockEntitySupplier<T> factory, Block... validBlocks) {
        return new BlockEntityType<>(factory, Set.of(validBlocks));
    }
}
