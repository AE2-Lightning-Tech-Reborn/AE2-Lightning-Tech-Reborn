package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.compat.orientation.IOrientationStrategy;
import com.moakiee.ae2lt.compat.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;

import com.moakiee.ae2lt.blockentity.PigmeeMentalmathUnitBlockEntity;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public class PigmeeMentalmathUnitBlock extends AE2LTBaseEntityBlock<PigmeeMentalmathUnitBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public PigmeeMentalmathUnitBlock() {
        super(AE2LTBaseEntityBlock.metalProps());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }
}
