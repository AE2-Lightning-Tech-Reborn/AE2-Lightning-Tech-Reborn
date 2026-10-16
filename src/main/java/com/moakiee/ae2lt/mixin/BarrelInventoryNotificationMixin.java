package com.moakiee.ae2lt.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.logic.BarrelInventoryNotificationBatch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockEntity.class)
public abstract class BarrelInventoryNotificationMixin {
    @WrapOperation(
            method = "setChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;blockEntityChanged(Lnet/minecraft/core/BlockPos;)V"))
    private static void ae2lt$batchBarrelDirtyMark(Level level, BlockPos pos, Operation<Void> original,
                                                Level callerLevel, BlockPos callerPos, BlockState state) {
        if (!BarrelInventoryNotificationBatch.alreadyMarkedDirty(level, pos, state.getBlock())) original.call(level, pos);
    }

    @WrapOperation(
            method = "setChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;updateNeighbourForOutputSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;)V"))
    private static void ae2lt$batchBarrelNotification(Level level, BlockPos pos, Block block, Operation<Void> original) {
        if (!BarrelInventoryNotificationBatch.defer(level, pos, block)) original.call(level, pos, block);
    }
}
