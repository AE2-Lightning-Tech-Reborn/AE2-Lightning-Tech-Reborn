package com.moakiee.ae2lt.mixin.ae2cs;

import com.moakiee.ae2lt.integration.ae2cs.OverclockPass;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Additional recipe passes must not repeat CS's automatic I/O or network tick. */
@Mixin(targets = "io.github.lounode.ae2cs.common.block.entity.AENetworkedComponentBlockEntity", remap = false)
public abstract class CrystalScienceComponentTickMixin {
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void ae2lt$skipExtraComponentTick(CallbackInfo ci) {
        if (this instanceof OverclockPass pass && pass.ae2lt$isExtraPass()) {
            ci.cancel();
        }
    }
}
