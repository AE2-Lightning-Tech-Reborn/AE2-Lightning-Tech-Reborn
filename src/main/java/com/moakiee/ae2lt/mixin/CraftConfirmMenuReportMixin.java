package com.moakiee.ae2lt.mixin;

import java.util.concurrent.Future;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IGrid;
import appeng.api.storage.ISubMenuHost;
import net.minecraft.world.entity.player.Inventory;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.crafting.CraftConfirmMenu;

import com.moakiee.ae2lt.crafting.report.CraftingReportDiagnostics;
import com.moakiee.ae2lt.crafting.report.CraftingReportMenuState;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.thunderbolt.api.crafting.CraftingPlanningEngines;
import com.moakiee.thunderbolt.core.crafting.algorithm.CraftingAlgorithmCalculationStatus;

// Data Energistics merges an additional long-amount entry point at priority 1000.
@Mixin(value = CraftConfirmMenu.class, remap = false, priority = 1100)
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
        // The report belongs to Thunderbolt-proxied planning. Merely having a Tianshu CPU
        // on the grid must not replace AE2's native confirmation screen when the player
        // explicitly selected the vanilla planner.
        var selectedPlanner = CraftingAlgorithmCalculationStatus.selected(job);
        // AE2 clears the completed Future after building the summary. Once that happens the
        // calculation-status lookup returns null, so retain the last resolved choice instead of
        // resetting the client back to the native screen on the following broadcast tick.
        if (selectedPlanner != null) {
            ae2lt$showReport = hasTianshu
                    && !CraftingPlanningEngines.VANILLA_ID.equals(selectedPlanner);
        }
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

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2lt$initializeReportTimer(
            int id, Inventory inventory, ISubMenuHost host, CallbackInfo ci) {
        // Some callers install a pre-started Future through setJob rather than planJob.
        // Start timing when the menu is created so that path cannot fall back to nanoTime() - 0.
        ae2lt$calculationStartedNanos = System.nanoTime();
    }

    @Inject(method = {"planJob", "data_energistics$planJob"}, at = @At("HEAD"))
    private void ae2lt$resetReportDiagnostics(
            CallbackInfoReturnable<Boolean> cir) {
        ae2lt$showReport = false;
        ae2lt$calculationNanos = 0L;
        ae2lt$calculationStartedNanos = System.nanoTime();
    }

    @Inject(
            method = "broadcastChanges",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/menu/me/crafting/CraftingPlanSummary;fromJob("
                            + "Lappeng/api/networking/IGrid;"
                            + "Lappeng/api/networking/security/IActionSource;"
                            + "Lappeng/api/networking/crafting/ICraftingPlan;)"
                            + "Lappeng/menu/me/crafting/CraftingPlanSummary;"))
    private void ae2lt$captureReportDiagnostics(CallbackInfo ci) {
        if (ae2lt$calculationNanos == 0L && result != null && job != null && job.isDone()) {
            // AE2 clears job immediately after building the summary, so capture the finished
            // calculation before that write. The menu-wall-clock value covers planners that do
            // not execute CraftingCalculation and is only used when no worker measurement exists.
            long fallbackNanos = ae2lt$calculationStartedNanos > 0L
                    ? Math.max(0L, System.nanoTime() - ae2lt$calculationStartedNanos)
                    : 0L;
            long measuredNanos = CraftingReportDiagnostics.take(result, fallbackNanos);
            if (measuredNanos > 0L) {
                ae2lt$calculationNanos = measuredNanos;
            }
        }
    }

    @Override
    public long ae2lt$getCalculationNanos() {
        return ae2lt$calculationNanos;
    }
}
