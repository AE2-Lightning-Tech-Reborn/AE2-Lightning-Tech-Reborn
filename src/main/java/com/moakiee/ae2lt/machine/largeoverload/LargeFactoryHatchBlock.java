package com.moakiee.ae2lt.machine.largeoverload;

import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class LargeFactoryHatchBlock extends AEBaseEntityBlock<LargeFactoryHatchBlockEntity> implements LargeFactoryRegistration.Part {
    private final LargeFactoryComponent component;
    public LargeFactoryHatchBlock(Properties properties, LargeFactoryComponent component) {
        super(properties);
        this.component = component;
        registerDefaultState(defaultBlockState().setValue(LargeFactoryPartBlock.FORMED, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LargeFactoryPartBlock.FORMED);
    }
    @Override protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(LargeFactoryPartBlock.FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }
    @Override protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LargeFactoryPartBlock.FORMED) ? Shapes.empty() : super.getOcclusionShape(state, level, pos);
    }
    @Override protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LargeFactoryPartBlock.FORMED) || super.propagatesSkylightDown(state, level, pos);
    }
    @Override protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LargeFactoryPartBlock.FORMED) ? 0 : super.getLightBlock(state, level, pos);
    }
    @Override protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LargeFactoryPartBlock.FORMED) ? 1 : super.getShadeBrightness(state, level, pos);
    }
    @Override public LargeFactoryComponent component() { return component; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return LargeFactoryMenu.open(level, pos, player);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof LargeFactoryHatchBlockEntity hatch) hatch.releaseResources();
        super.onRemove(state, level, pos, next, moved);
    }
}
