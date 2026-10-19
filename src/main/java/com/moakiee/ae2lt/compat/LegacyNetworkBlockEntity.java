package com.moakiee.ae2lt.compat;

import appeng.blockentity.grid.AENetworkBlockEntity;
import com.moakiee.ae2lt.compat.orientation.BlockOrientation;
import com.moakiee.ae2lt.compat.orientation.IOrientationStrategy;
import com.moakiee.ae2lt.compat.orientation.RelativeSide;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.EnumSet;
import java.util.Set;

/** Keeps the blockstate orientation and AE2 12's node sides in sync. */
public class LegacyNetworkBlockEntity extends AENetworkBlockEntity {
    public LegacyNetworkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public void clearContent() {}
    public BlockOrientation getOrientation() { return BlockOrientation.get(getBlockState()); }
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) { return EnumSet.allOf(Direction.class); }
    protected void onOrientationChanged(BlockOrientation orientation) {
        getMainNode().setExposedOnSides(getGridConnectableSides(orientation));
    }
    public void onGridConnectableSidesChanged() { onOrientationChanged(getOrientation()); }
    @Override public void onReady() {
        onOrientationChanged(getOrientation());
        super.onReady();
    }
    @Override public Direction getForward() { return getOrientation().getSide(RelativeSide.FRONT); }
    @Override public Direction getUp() { return getOrientation().getSide(RelativeSide.TOP); }
    @Override public void setOrientation(Direction forward, Direction up) {
        var state = getBlockState();
        var rotated = IOrientationStrategy.get(state).setOrientation(state, forward, up);
        if (level != null && rotated != state) level.setBlockAndUpdate(worldPosition, rotated);
        super.setOrientation(forward, up);
        onOrientationChanged(BlockOrientation.get(rotated));
    }
    @Override public void setBlockState(BlockState state) {
        var previous = getBlockState();
        super.setBlockState(state);
        if (previous != state && getMainNode() != null) onOrientationChanged(BlockOrientation.get(state));
    }
}
