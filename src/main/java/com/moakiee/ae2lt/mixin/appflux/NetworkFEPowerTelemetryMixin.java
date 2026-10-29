package com.moakiee.ae2lt.mixin.appflux;

import appeng.api.networking.security.IActionSource;
import com.moakiee.ae2lt.logic.management.PowerTelemetry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Passive extraction has no destination address in the FE capability API; retain that distinction. */
@Pseudo
@Mixin(targets = "com.glodblock.github.appflux.common.caps.NetworkFEPower", remap = false)
public abstract class NetworkFEPowerTelemetryMixin {
    @Shadow @Final private IActionSource source;
    @Inject(method = "extractEnergy", at = @At("RETURN"), remap = false)
    private void ae2lt$receipt(int maximum, boolean simulate, CallbackInfoReturnable<Integer> cir) {
        if (!simulate) PowerTelemetry.record(source, null, cir.getReturnValueI(), true);
    }
}
