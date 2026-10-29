package com.moakiee.ae2lt.mixin.appflux;

import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import com.glodblock.github.appflux.common.me.energy.EnergyCapCache;
import com.moakiee.ae2lt.logic.management.PowerTelemetry;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers native induction cards and both block/part flux accessors at their shared receipt boundary. */
@Pseudo
@Mixin(targets = "com.glodblock.github.appflux.common.me.energy.EnergyHandler", remap = false)
public abstract class EnergyHandlerTelemetryMixin {
    @Inject(method = "send", at = @At("RETURN"), remap = false)
    private static void ae2lt$receipt(EnergyCapCache cache, Direction side, IStorageService storage,
            IActionSource source, CallbackInfoReturnable<Long> cir) {
        if (cir.getReturnValueJ() <= 0) return;
        var location = (EnergyCapCacheAccessor) cache;
        PowerTelemetry.record(source, new PowerTelemetry.Target(location.ae2lt$world().dimension().location().toString(),
                location.ae2lt$pos().relative(side), side.getOpposite().ordinal()), cir.getReturnValueJ(), false);
    }
}
