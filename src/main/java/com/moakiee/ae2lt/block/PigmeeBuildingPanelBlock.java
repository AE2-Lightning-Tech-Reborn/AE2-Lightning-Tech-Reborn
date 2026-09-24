package com.moakiee.ae2lt.block;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;

/** A solid decorative block; connectivity is handled entirely by its baked model. */
public final class PigmeeBuildingPanelBlock extends Block {
    private final DyeColor color;
    private final boolean framed;

    public PigmeeBuildingPanelBlock(DyeColor color, boolean framed) {
        super(com.moakiee.ae2lt.registry.ModBlocks.registeredProperties(Properties.of().mapColor(color.getMapColor()).strength(1.5F, 6.0F).sound(SoundType.STONE)
                .requiresCorrectToolForDrops()));
        this.color = color;
        this.framed = framed;
    }

    public DyeColor color() {
        return color;
    }

    public boolean framed() {
        return framed;
    }

    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            java.util.function.Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.ae2lt.pigmee_building_panel")
                .withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("tooltip.ae2lt.pigmee_building_panel.restore")
                .withStyle(ChatFormatting.GRAY));
    }
}
