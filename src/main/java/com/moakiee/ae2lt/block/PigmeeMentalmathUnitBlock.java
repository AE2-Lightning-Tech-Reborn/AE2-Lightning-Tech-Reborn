package com.moakiee.ae2lt.block;

import java.util.List;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;

import com.moakiee.ae2lt.blockentity.PigmeeMentalmathUnitBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class PigmeeMentalmathUnitBlock extends AEBaseEntityBlock<PigmeeMentalmathUnitBlockEntity> {
    public static final EnumProperty<net.minecraft.core.Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public PigmeeMentalmathUnitBlock() {
        super(com.moakiee.ae2lt.registry.ModBlocks.registeredProperties(metalProps(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()).forceSolidOn()));
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            java.util.function.Consumer<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.accept(Component.translatable("tooltip.ae2lt.pigmee_mentalmath_unit.1")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.accept(Component.translatable("tooltip.ae2lt.pigmee_mentalmath_unit.2")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
