package com.moakiee.ae2lt.mixin;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.helpers.WirelessTerminalMenuHost;
import com.moakiee.ae2lt.logic.wireless.ControllerWirelessCoverage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forge AE2 checks coverage through rangeCheck, including WTLib's native fallback. */
@Mixin(value = WirelessTerminalMenuHost.class, remap = false)
public abstract class WirelessTerminalCoverageMixin {
    @Shadow private IWirelessAccessPoint myWap;
    @Shadow private double currentDistanceFromGrid;
    @Shadow @Final private IGrid targetGrid;

    @Inject(method = "rangeCheck", at = @At("RETURN"), cancellable = true)
    private void ae2lt$controllerCoverage(CallbackInfoReturnable<Boolean> cir) {
        var host = (WirelessTerminalMenuHost) (Object) this;
        if (cir.getReturnValueZ() || host.isClientSide() || targetGrid == null) return;
        var controller = ControllerWirelessCoverage.find(targetGrid, host.getPlayer().level());
        if (controller == null) return;
        myWap = controller;
        currentDistanceFromGrid = ControllerWirelessCoverage.powerDistance();
        cir.setReturnValue(true);
    }
}
