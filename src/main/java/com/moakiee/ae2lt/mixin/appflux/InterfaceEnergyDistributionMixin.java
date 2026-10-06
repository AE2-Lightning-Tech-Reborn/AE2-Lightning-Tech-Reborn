package com.moakiee.ae2lt.mixin.appflux;

import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applied Flux 1.20.1 merges its distributor into InterfaceLogic. */
@Mixin(value = InterfaceLogic.class, priority = 900, remap = false)
public abstract class InterfaceEnergyDistributionMixin {
    @Shadow protected InterfaceLogicHost host;

    @Inject(method = "distribute()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2lt$skipAdjacentWirelessOutput(CallbackInfo ci) {
        if (host instanceof OverloadedInterfaceBlockEntity overloaded
                && overloaded.getInterfaceMode() == OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS) {
            ci.cancel();
        }
    }
}
