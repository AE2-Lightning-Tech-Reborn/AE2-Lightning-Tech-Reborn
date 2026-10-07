package com.moakiee.ae2lt.mixin.appflux;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.glodblock.github.appflux.common.me.energy.EnergyCapCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps Applied Flux's adjacent output consistent with the interface's target selection. */
@Pseudo
@Mixin(targets = "com.glodblock.github.appflux.common.me.energy.EnergyTicker", remap = false)
public abstract class EnergyTickerMixin {
    @Shadow(remap = false) @Final private Object host;
    @Unique private EnergyCapCache ae2lt$normalCache;

    @Inject(method = "distribute", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2lt$skipSeparatelyHandledOutput(long tick, CallbackInfo ci) {
        if (host instanceof OverloadedInterfaceBlockEntity overloaded
                && (overloaded.getInterfaceMode() == OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS
                    || overloaded.getTargetDirection() != null)) {
            // Explicit local directions use the block ticker; do not send a
            // second time here or leak FE through other adjacent faces.
            ci.cancel();
        }
    }

    @WrapOperation(method = "distribute", at = @At(value = "INVOKE", target =
            "Lcom/glodblock/github/appflux/common/me/energy/EnergyHandler;send("
            + "Lcom/glodblock/github/appflux/common/me/energy/EnergyCapCache;"
            + "Lnet/minecraft/core/Direction;Lappeng/api/networking/storage/IStorageService;"
            + "Lappeng/api/networking/security/IActionSource;)J"), remap = false)
    private long ae2lt$filterNormalTarget(EnergyCapCache cache, Direction side, IStorageService storage,
                                         IActionSource source, Operation<Long> original) {
        if (!(host instanceof OverloadedInterfaceBlockEntity overloaded)) {
            return original.call(cache, side, storage, source);
        }
        if (!overloaded.allowsNormalInteraction(side)
                || !(overloaded.getLevel() instanceof ServerLevel level)) {
            // Zero keeps the existing retry cadence. -1 permanently blocks a
            // side until capability invalidation, missing grid-only changes.
            return 0L;
        }
        if (ae2lt$normalCache == null) {
            // Our policy allows ordinary same-grid machines. Retain AppFlux's
            // capability caching without its blanket same-grid rejection.
            ae2lt$normalCache = new EnergyCapCache(level, overloaded.getBlockPos(), () -> null);
        }
        return original.call(ae2lt$normalCache, side, storage, source);
    }
}
