package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockUpdateScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class TianshuSupercomputerStructureBlock extends Block implements WrenchDisassemblableBlock {
    public static final BooleanProperty FORMED = MultiblockStateProperties.FORMED;

    public TianshuSupercomputerStructureBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock())) {
            TianshuMultiblockUpdateScheduler.scheduleNear(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean movedByPiston) {
        TianshuMultiblockUpdateScheduler.scheduleNear(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader level,
            net.minecraft.world.level.ScheduledTickAccess tickAccess, BlockPos pos,
            net.minecraft.core.Direction direction, BlockPos neighborPos, BlockState neighborState,
            net.minecraft.util.RandomSource random) {
        if (level instanceof Level world) {
            TianshuMultiblockUpdateScheduler.scheduleNear(world, neighborPos);
        }
        return super.updateShape(state, level, tickAccess, pos, direction, neighborPos, neighborState, random);
    }
}
