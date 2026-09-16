package com.moakiee.ae2lt.mixin;

import java.util.concurrent.Future;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.crafting.CraftConfirmMenu;

import com.moakiee.ae2lt.crafting.report.CraftingReportDiagnostics;
import com.moakiee.ae2lt.crafting.report.CraftingReportMenuState;

@Mixin(value = CraftConfirmMenu.class, remap = false)
public abstract class CraftConfirmMenuReportMixin implements CraftingReportMenuState {
    @Shadow
    private Future<ICraftingPlan> job;

    @Shadow
    private ICraftingPlan result;

    @Unique
    @GuiSync(30_100)
    private long ae2lt$calculationNanos;

    @Unique
    private long ae2lt$calculationStartedNanos;

    @Inject(method = "planJob", at = @At("HEAD"))
    private void ae2lt$resetReportDiagnostics(
            AEKey what, int amount, CalculationStrategy strategy,
            CallbackInfoReturnable<Boolean> cir) {
        ae2lt$calculationNanos = 0L;
        ae2lt$calculationStartedNanos = System.nanoTime();
    }

    @Inject(method = "broadcastChanges", at = @At("TAIL"))
    private void ae2lt$captureReportDiagnostics(CallbackInfo ci) {
        if (ae2lt$calculationNanos == 0L && result != null && job != null && job.isDone()) {
            ae2lt$calculationNanos = CraftingReportDiagnostics.take(
                    result, System.nanoTime() - ae2lt$calculationStartedNanos);
        }
    }

    @Override
    public long ae2lt$getCalculationNanos() {
        return ae2lt$calculationNanos;
    }
}
