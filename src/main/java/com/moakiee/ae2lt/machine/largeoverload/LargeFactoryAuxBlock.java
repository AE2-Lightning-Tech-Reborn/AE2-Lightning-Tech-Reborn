package com.moakiee.ae2lt.machine.largeoverload;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class LargeFactoryAuxBlock extends LargeFactoryPartBlock implements EntityBlock {
    public LargeFactoryAuxBlock(Properties properties, LargeFactoryComponent component) { super(properties, component); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LargeFactoryAuxBlockEntity(pos, state); }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        return LargeFactoryMenu.open(level, pos, player);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof LargeFactoryAuxBlockEntity hatch && !level.isClientSide) hatch.dropContents();
        super.onRemove(state, level, pos, next, moved);
    }
}
