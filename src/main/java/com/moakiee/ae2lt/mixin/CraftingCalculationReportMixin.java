package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.crafting.CraftingCalculation;

import com.moakiee.ae2lt.crafting.report.CraftingReportDiagnostics;

@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class CraftingCalculationReportMixin {
    @Unique
    private long ae2lt$calculationStartedNanos;

    @Inject(method = "run", at = @At("HEAD"), remap = false)
    private void ae2lt$startReportTimer(CallbackInfoReturnable<ICraftingPlan> cir) {
        ae2lt$calculationStartedNanos = System.nanoTime();
    }

    @Inject(method = "run", at = @At("RETURN"), remap = false)
    private void ae2lt$recordReportTimer(CallbackInfoReturnable<ICraftingPlan> cir) {
        ICraftingPlan plan = cir.getReturnValue();
        if (plan != null) {
            CraftingReportDiagnostics.record(
                    plan, System.nanoTime() - ae2lt$calculationStartedNanos);
        }
    }
}
