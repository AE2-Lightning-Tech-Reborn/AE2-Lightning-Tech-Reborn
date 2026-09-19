package com.moakiee.ae2lt.mixin.gtlcore;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;

import com.moakiee.ae2lt.crafting.runtime.ClosedLoopCpuSubmitGuard;

/**
 * GTLCore's leftover submit will auto-select a Transfinite controller for any
 * {@code ICraftingPlan} that Thunderbolt did not claim. Closed-loop jobs still
 * need host-seed extraction, so Transfinite must refuse them.
 */
@Pseudo
@Mixin(
        targets = "org.gtlcore.gtlcore.integration.ae2.crafting.transfinite.TransfiniteCraftingLogic",
        remap = false)
public abstract class TransfiniteCraftingLogicMixin {
    @Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true)
    private void ae2lt$rejectClosedLoopPlan(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource source,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        ClosedLoopCpuSubmitGuard.rejectIfClosedLoop(plan, cir);
    }
}
