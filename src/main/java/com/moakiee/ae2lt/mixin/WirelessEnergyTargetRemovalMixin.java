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
    @Inject(method = "removeBlockEntity", at = @At("HEAD"))
    private void ae2lt$removeEnergyGeneration(BlockPos pos, CallbackInfo ci) {
        if (((LevelChunk) (Object) this).getLevel() instanceof ServerLevel level) {
            WirelessEnergyEndpoints.onRemoved(level, pos);
        }
    }
}
