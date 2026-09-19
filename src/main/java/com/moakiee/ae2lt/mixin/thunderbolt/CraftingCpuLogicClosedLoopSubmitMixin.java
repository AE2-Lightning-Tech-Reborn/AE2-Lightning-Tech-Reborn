package com.moakiee.ae2lt.mixin.thunderbolt;

import org.spongepowered.asm.mixin.Mixin;
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
 * Closed-loop leftover rejection lives in its own mixin so a missed
 * {@code WrapOperation} on GTLCore's {@code executeCrafting} overwrite cannot
 * drop this gate with {@code defaultRequire: 1}.
 */
@Mixin(value = appeng.crafting.execution.CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuLogicClosedLoopSubmitMixin {
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
