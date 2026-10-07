package com.moakiee.ae2lt.machine.largeoverload;

import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class LargeFactoryHatchBlock extends AEBaseEntityBlock<LargeFactoryHatchBlockEntity> implements LargeFactoryRegistration.Part {
    private final LargeFactoryComponent component;
    public LargeFactoryHatchBlock(Properties properties, LargeFactoryComponent component) { super(properties); this.component = component; }
    @Override public LargeFactoryComponent component() { return component; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return LargeFactoryMenu.open(level, pos, player);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof LargeFactoryHatchBlockEntity hatch) hatch.releaseResources();
        super.onRemove(state, level, pos, next, moved);
    }
}
