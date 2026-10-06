package com.moakiee.ae2lt.mixin.appflux;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents Applied Flux's adjacent output from bypassing the interface's wireless mode. */
@Pseudo
@Mixin(targets = "com.glodblock.github.appflux.common.me.energy.EnergyTicker", remap = false)
public abstract class EnergyTickerMixin {
    @Shadow(remap = false) @Final private Object host;

    @Inject(method = "distribute", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2lt$skipAdjacentOutputInWirelessMode(long tick, CallbackInfo ci) {
        if (host instanceof OverloadedInterfaceBlockEntity overloaded
                && overloaded.getInterfaceMode() == OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS) {
            ci.cancel();
        }
    }
}
