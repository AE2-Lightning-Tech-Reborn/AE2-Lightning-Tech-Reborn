package com.moakiee.ae2lt.block;

import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MaterialColor;

/** Vanilla slab placement, waterlogging and merging; the model provides decoration. */
public final class PigmeeBuildingSlabBlock extends SlabBlock {
    public PigmeeBuildingSlabBlock(MaterialColor color) {
        super(Properties.of(net.minecraft.world.level.material.Material.STONE).color(color).strength(1.5F, 6.0F).sound(SoundType.STONE)
                .requiresCorrectToolForDrops());
    }

}
