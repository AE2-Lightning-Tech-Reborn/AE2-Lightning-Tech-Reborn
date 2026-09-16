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
import appeng.api.networking.IGrid;
import appeng.api.storage.ISubMenuHost;
import net.minecraft.world.entity.player.Inventory;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.crafting.CraftConfirmMenu;

import com.moakiee.ae2lt.crafting.report.CraftingReportDiagnostics;
import com.moakiee.ae2lt.crafting.report.CraftingReportMenuState;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPool;

@Mixin(value = CraftConfirmMenu.class, remap = false)
public abstract class CraftConfirmMenuReportMixin implements CraftingReportMenuState {
    @Shadow
    private IGrid getGrid() {
        throw new AssertionError();
    }

    @Unique
    private boolean ae2lt$serverMenu;

    @Unique
    @GuiSync(30_101)
    private boolean ae2lt$showReport;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2lt$captureMenuSide(int id, Inventory inventory, ISubMenuHost host, CallbackInfo ci) {
        ae2lt$serverMenu = !inventory.player.level().isClientSide();
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void ae2lt$syncReportAvailability(CallbackInfo ci) {
        if (!ae2lt$serverMenu) {
            return;
        }
        IGrid grid = getGrid();
        boolean hasTianshu = false;
        if (grid != null) {
            for (var cpu : grid.getCraftingService().getCpus()) {
                hasTianshu |= cpu instanceof TimeWheelCraftingCPU || cpu instanceof TimeWheelCraftingCpuPool;
            }
        }
        ae2lt$showReport = hasTianshu;
    }

    @Override
    public boolean ae2lt$shouldShowReport() {
        return ae2lt$showReport;
    }

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
