package com.moakiee.ae2lt.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/** The basic Pigmee building block; a plain block without a block entity. */
public final class PigmeeBuildingBlock extends Block {
    public PigmeeBuildingBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_PINK).strength(1.5F, 6.0F).sound(SoundType.STONE)
                .requiresCorrectToolForDrops());
    }
}
