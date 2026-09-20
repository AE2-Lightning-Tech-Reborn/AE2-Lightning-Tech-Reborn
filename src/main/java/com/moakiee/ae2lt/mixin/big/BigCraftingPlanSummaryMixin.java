package com.moakiee.ae2lt.mixin.big;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.menu.me.crafting.CraftingPlanSummary;

import com.moakiee.ae2lt.crafting.big.BigCraftingPlan;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingPlanSummary.class, remap = false)
public abstract class BigCraftingPlanSummaryMixin {
    @Inject(method = "fromJob", at = @At("HEAD"), cancellable = true)
    private static void ae2lt$summary(
            IGrid grid,
            IActionSource src,
            ICraftingPlan plan,
            CallbackInfoReturnable<CraftingPlanSummary> cir) {
        if (plan instanceof BigCraftingPlan big) cir.setReturnValue(big.summary());
    }
}
