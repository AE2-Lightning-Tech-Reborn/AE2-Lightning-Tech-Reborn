package com.moakiee.ae2lt.block;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;

/** A solid decorative block; connectivity is handled entirely by its baked model. */
public final class PigmeeBuildingPanelBlock extends Block {
    private final DyeColor color;
    private final boolean framed;

    public PigmeeBuildingPanelBlock(DyeColor color, boolean framed) {
        super(Properties.of().mapColor(color.getMapColor()).strength(1.5F, 6.0F).sound(SoundType.STONE)
                .requiresCorrectToolForDrops());
        this.color = color;
        this.framed = framed;
    }

    public DyeColor color() {
        return color;
    }

    public boolean framed() {
        return framed;
    }

}
