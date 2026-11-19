package com.moakiee.ae2lt.mixin;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.helpers.WirelessTerminalMenuHost;
import com.moakiee.ae2lt.logic.wireless.ControllerWirelessCoverage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Also covers WTLib/Tianshu hosts, which call this method before their quantum fallback. */
@Mixin(WirelessTerminalMenuHost.class)
public abstract class WirelessTerminalCoverageMixin {
    @Shadow private IWirelessAccessPoint currentAccessPoint;
    @Shadow protected double currentDistanceFromGrid;
    @Shadow protected double currentRemainingRange;

    @Inject(method = "updateConnectedAccessPoint", at = @At("TAIL"), require = 1)
    private void ae2lt$controllerCoverage(CallbackInfo ci) {
        var host = (WirelessTerminalMenuHost<?>) (Object) this;
        if (host.isClientSide() || currentAccessPoint != null) return;
        // Resolve the item's actual link first; an unrelated covered grid must never grant access.
        var grid = host.getItem().getLinkedGrid(host.getItemStack(), host.getPlayer().level(), null);
        if (grid == null) return;
        var controller = ControllerWirelessCoverage.find(grid, host.getPlayer().level());
        if (controller == null) return;
        currentAccessPoint = controller;
        currentDistanceFromGrid = ControllerWirelessCoverage.powerDistance();
        currentRemainingRange = Double.POSITIVE_INFINITY;
        // Native updateLinkStatus/consumeIdlePower still decide whether the terminal has power.
    }
}
