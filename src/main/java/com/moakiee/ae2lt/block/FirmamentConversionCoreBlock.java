package com.moakiee.ae2lt.block;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.moakiee.ae2lt.blockentity.FirmamentConversionCoreBlockEntity;
import com.moakiee.ae2lt.registry.ModBlockEntities;

public class FirmamentConversionCoreBlock extends Block implements EntityBlock {
    public FirmamentConversionCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(MultiblockStateProperties.FORMED, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MultiblockStateProperties.FORMED);
    }
    @Override protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(MultiblockStateProperties.FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }
    @Override protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(MultiblockStateProperties.FORMED) ? Shapes.empty() : super.getOcclusionShape(state, level, pos);
    }
    @Override protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(MultiblockStateProperties.FORMED) || super.propagatesSkylightDown(state, level, pos);
    }
    @Override protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(MultiblockStateProperties.FORMED) ? 0 : super.getLightBlock(state, level, pos);
    }
    @Override protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(MultiblockStateProperties.FORMED) ? 1 : super.getShadeBrightness(state, level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult) {
        if (state.getValue(MultiblockStateProperties.FORMED)) {
            return com.moakiee.ae2lt.machine.largeoverload.LargeFactoryMenu.openController(level, pos, player);
        }
        if (!(level.getBlockEntity(pos) instanceof FirmamentConversionCoreBlockEntity be)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            be.extractToPlayer(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof FirmamentConversionCoreBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide() && !be.insertHeldItem(player, hand)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FirmamentConversionCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || blockEntityType != ModBlockEntities.FIRMAMENT_CONVERSION_CORE.get()) {
            return null;
        }
        return (l, p, s, be) ->
                FirmamentConversionCoreBlockEntity.serverTick(l, p, s, (FirmamentConversionCoreBlockEntity) be);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof FirmamentConversionCoreBlockEntity be) {
            be.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, builder));
        BlockEntity blockEntity = builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof FirmamentConversionCoreBlockEntity be) {
            be.addDrops(drops);
        }
        return drops;
    }
}
