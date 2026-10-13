package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.workstation.PigmeeRemoteView;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class PigmeeRemoteChunkTrackingMixin {
    @Inject(method = "isChunkTracked", at = @At("RETURN"), cancellable = true)
    private void ae2lt$remoteWatch(ServerPlayer viewer, int x, int z, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && PigmeeRemoteView.watches(viewer, x, z)) cir.setReturnValue(true);
    }
}
