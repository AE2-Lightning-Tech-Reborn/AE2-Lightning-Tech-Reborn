package com.moakiee.ae2lt.mixin;

import appeng.blockentity.crafting.CraftingBlockEntity;
import com.moakiee.ae2lt.compat.gto.GtoCompatibleCraftingCpuLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AE2 only drops getInventory(); LT also owns a separate direct-output buffer. */
@Mixin(value = CraftingBlockEntity.class, remap = false)
public abstract class GtoCraftingCpuDropsMixin {
    @Inject(method = "breakCluster", at = @At(value = "INVOKE",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;cancelJob()V",
            shift = At.Shift.AFTER), require = 1)
    private void ae2lt$dropAllLtContents(CallbackInfo ci) {
        var block = (CraftingBlockEntity) (Object) this;
        var cluster = block.getCluster();
        if (cluster != null && cluster.craftingLogic instanceof GtoCompatibleCraftingCpuLogic compatible) {
            compatible.dropStoredItemsForRemoval(block.getLevel(), block.getBlockPos());
        }
    }
}
