package com.moakiee.ae2lt.mixin;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.me.Grid;
import appeng.me.service.EnergyService;
import com.moakiee.ae2lt.logic.energy.WirelessAeFallback;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnergyService.class)
public abstract class RunningNetworkFeFallbackMixin {
    @Shadow @Final private Grid grid;
    @Shadow private boolean hasPower;
    @Shadow private boolean publicHasPower;
    @Shadow private double tickDrainPerTick;

    @Inject(method = "extractAEPower", at = @At("RETURN"), cancellable = true)
    private void ae2lt$supplementRunningGrid(double amount, Actionable mode, PowerMultiplier multiplier,
                                            CallbackInfoReturnable<Double> cir) {
        double original = cir.getReturnValueD();
        double supplied = WirelessAeFallback.supplement(grid, hasPower && publicHasPower,
                amount, original, mode, multiplier);
        if (mode == Actionable.MODULATE) tickDrainPerTick += multiplier.multiply(supplied - original);
        cir.setReturnValue(supplied);
    }
}
