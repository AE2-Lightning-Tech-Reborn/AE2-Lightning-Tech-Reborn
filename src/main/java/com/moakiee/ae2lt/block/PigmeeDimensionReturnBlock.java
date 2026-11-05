package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.dimension.PigmeeDimensionTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;

/** An indestructible, itemless return point, rather than a renewable copy of an entry block. */
public final class PigmeeDimensionReturnBlock extends Block {
    public PigmeeDimensionReturnBlock() {
        super(Properties.of().strength(-1.0F, 3600000.0F).noLootTable().pushReaction(PushReaction.BLOCK));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) PigmeeDimensionTravel.leave(serverPlayer);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
