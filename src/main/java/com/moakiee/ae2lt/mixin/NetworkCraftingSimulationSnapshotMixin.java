package com.moakiee.ae2lt.mixin;

import appeng.api.networking.storage.IStorageService;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.moakiee.thunderbolt.compat.gto.inv.NetworkCraftingSimulationState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.crafting.report.CraftingReportInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Captures the original stock, or supplies the reduced stock for a Tianshu report replan. */
@Mixin(value = NetworkCraftingSimulationState.class, remap = false)
public abstract class NetworkCraftingSimulationSnapshotMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lappeng/api/storage/MEStorage;extract(Lappeng/api/stacks/AEKey;J"
                    + "Lappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J"))
    private long ae2lt$extractableSnapshot(MEStorage storage, AEKey what, long amount,
            Actionable mode, IActionSource source, Operation<Long> original) {
        return CraftingReportInventory.readExtractableAmount(what, amount,
                () -> original.call(storage, what, amount, mode, source));
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/storage/IStorageService;getCachedInventory()Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter ae2lt$cachedSnapshot(IStorageService storage, Operation<KeyCounter> original) {
        return CraftingReportInventory.readAvailableStacks(() -> original.call(storage));
    }
}
