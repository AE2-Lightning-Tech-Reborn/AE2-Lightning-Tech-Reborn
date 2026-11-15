package com.moakiee.ae2lt.machine.largeoverload;

import com.moakiee.ae2lt.block.MultiblockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LargeFactoryPartBlock extends Block implements LargeFactoryRegistration.Part {
    public static final BooleanProperty FORMED = MultiblockStateProperties.FORMED;
    private final LargeFactoryComponent component;
    public LargeFactoryPartBlock(Properties properties, LargeFactoryComponent component) {
        super(properties);
        this.component = component;
        registerDefaultState(defaultBlockState().setValue(FORMED, false));
    }
    @Override public LargeFactoryComponent component() { return component; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FORMED); }
    @Override protected RenderShape getRenderShape(BlockState state) { return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL; }
    @Override protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(FORMED) ? Shapes.empty() : super.getOcclusionShape(state, level, pos);
    }
    @Override protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(FORMED) || super.propagatesSkylightDown(state, level, pos);
    }
    @Override protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(FORMED) ? 0 : super.getLightBlock(state, level, pos);
    }
    @Override protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(FORMED) ? 1 : super.getShadeBrightness(state, level, pos);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return state.getValue(FORMED) ? LargeFactoryMenu.openController(level, pos, player) : InteractionResult.PASS;
    }
}
