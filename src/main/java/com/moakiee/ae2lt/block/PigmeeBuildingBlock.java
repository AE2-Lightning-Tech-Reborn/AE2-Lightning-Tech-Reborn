package com.moakiee.ae2lt.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MaterialColor;

/** The basic Pigmee building block; a plain block without a block entity. */
public final class PigmeeBuildingBlock extends Block {
    public PigmeeBuildingBlock() {
        super(Properties.of(net.minecraft.world.level.material.Material.STONE).color(MaterialColor.COLOR_PINK).strength(1.5F, 6.0F).sound(SoundType.STONE)
                .requiresCorrectToolForDrops());
    }

}
