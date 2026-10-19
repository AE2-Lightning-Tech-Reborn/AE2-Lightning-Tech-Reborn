package com.moakiee.ae2lt.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.AEBaseBlockEntity;

/**
 * Splits AE2's onActivated entrypoint into useItemOn / useWithoutItem hooks.
 * useItemOn returns null to fall through to useWithoutItem.
 */
public abstract class AE2LTBaseEntityBlock<T extends AEBaseBlockEntity> extends AEBaseEntityBlock<T> implements com.moakiee.ae2lt.compat.orientation.IOrientableBlock {
    protected AE2LTBaseEntityBlock(Properties properties) {
        super(properties);
    }

    public static Properties metalProps() { return defaultProps(net.minecraft.world.level.material.Material.METAL); }
    public static Properties stoneProps() { return defaultProps(net.minecraft.world.level.material.Material.STONE); }
    @Override
    public com.moakiee.ae2lt.compat.orientation.IOrientationStrategy getOrientationStrategy() {
        return com.moakiee.ae2lt.compat.orientation.OrientationStrategies.none();
    }
    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        getOrientationStrategy().getProperties().forEach(builder::add);
    }
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return getOrientationStrategy().getStateForPlacement(defaultBlockState(), context);
    }
    @Override
    public final InteractionResult onActivated(Level level, BlockPos pos, Player player,
            InteractionHand hand, @Nullable ItemStack heldItem, BlockHitResult hit) {
        var state = level.getBlockState(pos);
        var stack = heldItem == null ? ItemStack.EMPTY : heldItem;

        if (!stack.isEmpty()) {
            var itemResult = useItemOn(stack, state, level, pos, player, hand, hit);
            if (itemResult != null) {
                return itemResult;
            }
        }

        return useWithoutItem(state, level, pos, player, hit);
    }

    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    @Nullable
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return null;
    }
}

