package com.moakiee.ae2lt.mixin;

import java.util.ArrayList;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.crafting.CraftingStatus;
import appeng.menu.me.crafting.CraftingStatusEntry;
import com.moakiee.ae2lt.compat.gto.GtoCompatibleCraftingCpuLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingStatus.class, remap = false)
public abstract class GtoCraftingStatusMixin {
    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private static void ae2lt$keepOverloadStatusKeys(IncrementalUpdateHelper changes, CraftingCpuLogic logic,
            CallbackInfoReturnable<CraftingStatus> cir) {
        if (!(logic instanceof GtoCompatibleCraftingCpuLogic compatible) || !compatible.usesLt()) return;
        var entries = new ArrayList<CraftingStatusEntry>();
        for (var key : changes) {
            var entry = new CraftingStatusEntry(changes.getOrAssignSerial(key), key,
                    logic.getStored(key), logic.getWaitingFor(key), logic.getPendingOutputs(key));
            entries.add(entry);
            if (entry.isDeleted()) changes.removeSerial(key);
        }
        var tracker = logic.getElapsedTimeTracker();
        cir.setReturnValue(new CraftingStatus(changes.isFullUpdate(), tracker.getElapsedTime(),
                tracker.getRemainingItemCount(), tracker.getStartItemCount(), entries));
    }
}
