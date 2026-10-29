package com.moakiee.ae2lt.mixin.appflux;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.logic.management.AppFluxTelemetry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import sonar.fluxnetworks.api.energy.IFNEnergyStorage;

@Pseudo
@Mixin(targets = "com.glodblock.github.appflux.common.me.energy.EnergyHandler", remap = false)
public abstract class ForgeFluxNetworkTelemetryMixin {
    @WrapOperation(method = "lambda$static$1", at = @At(value = "INVOKE",
            target = "Lsonar/fluxnetworks/api/energy/IFNEnergyStorage;receiveEnergyL(JZ)J"))
    private static long ae2lt$receipt(IFNEnergyStorage receiver, long amount, boolean simulate, Operation<Long> original) {
        long accepted = original.call(receiver, amount, simulate);
        if (!simulate && accepted >= 0 && accepted <= amount) AppFluxTelemetry.record(accepted);
        return accepted;
    }
}
