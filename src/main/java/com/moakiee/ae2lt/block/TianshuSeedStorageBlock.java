package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.blockentity.TianshuSeedStorageBlockEntity;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Cooling-position drive used by a formed Tianshu for reusable closed-loop seeds. */
public final class TianshuSeedStorageBlock extends TianshuSupercomputingUnitBlock implements EntityBlock {
    public TianshuSeedStorageBlock(Properties properties) {
        super(properties, TianshuMultiblockComponent.CLOSED_LOOP_SEED_STORAGE);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TianshuSeedStorageBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof TianshuSeedStorageBlockEntity drive) {
            if (!level.isClientSide()) drive.openMenu(player);
            return (level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER);
        }
        return InteractionResult.PASS;
    }


}
