package com.moakiee.ae2lt.mixin;

import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.NetworkCraftingSimulationState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.crafting.report.CraftingReportInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Captures the original stock, or supplies the reduced stock for a Tianshu report replan. */
@Mixin(value = NetworkCraftingSimulationState.class, remap = false)
public abstract class NetworkCraftingSimulationSnapshotMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/storage/IStorageService;getCachedInventory()Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter ae2lt$cachedSnapshot(IStorageService storage, Operation<KeyCounter> original) {
        return CraftingReportInventory.readAvailableStacks(() -> original.call(storage));
    }
}
