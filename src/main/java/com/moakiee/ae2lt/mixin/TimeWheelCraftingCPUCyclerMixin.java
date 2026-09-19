package com.moakiee.ae2lt.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.menu.me.crafting.CraftingCPURecord;

import com.moakiee.ae2lt.crafting.timewheel.TimeWheelManualCpuSelection;

@Mixin(targets = "appeng.menu.me.crafting.CraftingCPUCycler", remap = false)
public abstract class TimeWheelCraftingCPUCyclerMixin {
    @Shadow @Final
    private List<CraftingCPURecord> cpus;

    @Shadow
    private int selectedCpu;

    @Shadow
    private void notifyListener() { throw new AssertionError(); }

    @Unique
    private CraftingCPURecord ae2lt$capturedSelection;

    @Unique
    private final TimeWheelManualCpuSelection ae2lt$manualSelection = new TimeWheelManualCpuSelection();

    @Inject(method = "detectAndSendChanges", at = @At("HEAD"))
    private void ae2lt$captureSelectedPool(IGrid grid, CallbackInfo ci) {
        ae2lt$capturedSelection = ae2lt$manualSelection.capture(
                cpus, selectedCpu, TimeWheelCraftingCPUCyclerMixin::ae2lt$cpuOf);
    }

    @Inject(method = "detectAndSendChanges", at = @At(
            value = "INVOKE",
            target = "Lappeng/menu/me/crafting/CraftingCPUCycler;notifyListener()V"))
    private void ae2lt$restoreSelectedPool(IGrid grid, CallbackInfo ci) {
        // Reconcile before the menu sees the selection, including the empty-list case.
        selectedCpu = ae2lt$manualSelection.restore(
                cpus, selectedCpu, ae2lt$capturedSelection, TimeWheelCraftingCPUCyclerMixin::ae2lt$cpuOf);
        ae2lt$capturedSelection = null;
    }

    @Inject(method = "detectAndSendChanges", at = @At("RETURN"))
    private void ae2lt$releaseSelectionSnapshot(IGrid grid, CallbackInfo ci) {
        // Capacity can recover without changing list membership: the retained row is already
        // present, so AE2 need not rebuild. Refresh its display instead of leaving stale bytes.
        if (ae2lt$capturedSelection != null) {
            var record = (CraftingCPURecordAccessor) ae2lt$capturedSelection;
            var cpu = record.ae2lt$getCpu();
            if (record.ae2lt$getSize() != cpu.getAvailableStorage()
                    || record.ae2lt$getProcessors() != cpu.getCoProcessors()) {
                selectedCpu = ae2lt$manualSelection.restore(
                        cpus, selectedCpu, ae2lt$capturedSelection, TimeWheelCraftingCPUCyclerMixin::ae2lt$cpuOf);
                notifyListener();
            }
        }
        ae2lt$capturedSelection = null;
    }

    @Inject(method = "cycleCpu", at = @At("HEAD"))
    private void ae2lt$leaveUnavailableSelection(boolean forward, CallbackInfo ci) {
        selectedCpu = ae2lt$manualSelection.prepareCycle(cpus, selectedCpu, forward);
    }

    @Unique
    private static ICraftingCPU ae2lt$cpuOf(CraftingCPURecord record) {
        return ((CraftingCPURecordAccessor) record).ae2lt$getCpu();
    }
}
