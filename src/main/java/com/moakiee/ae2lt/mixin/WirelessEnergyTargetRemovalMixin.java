package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.logic.energy.WirelessEnergyEndpoints;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public abstract class WirelessEnergyTargetRemovalMixin {
    @Inject(method = "setBlockEntity", at = @At("HEAD"))
    private void ae2lt$replaceEnergyGeneration(net.minecraft.world.level.block.entity.BlockEntity replacement, CallbackInfo ci) {
        var chunk = (LevelChunk) (Object) this;
        var previous = chunk.getBlockEntities().get(replacement.getBlockPos());
        if (previous != null && previous != replacement && chunk.getLevel() instanceof ServerLevel level) {
            WirelessEnergyEndpoints.onRemoved(level, replacement.getBlockPos());
        }
    }

    @Inject(method = "removeBlockEntity", at = @At("HEAD"))
    private void ae2lt$removeEnergyGeneration(BlockPos pos, CallbackInfo ci) {
        if (((LevelChunk) (Object) this).getLevel() instanceof ServerLevel level) {
            WirelessEnergyEndpoints.onRemoved(level, pos);
        }
    }
}
