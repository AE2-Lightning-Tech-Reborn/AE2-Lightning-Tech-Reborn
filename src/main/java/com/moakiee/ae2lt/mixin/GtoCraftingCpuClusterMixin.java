package com.moakiee.ae2lt.mixin;

import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.moakiee.ae2lt.compat.gto.GtoCompatibleCraftingCpuLogic;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wrap the initialized native CPU after GTO and optional NeoECO have installed their hooks. */
@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 1100)
public abstract class GtoCraftingCpuClusterMixin {
    @Shadow public CraftingCpuLogic craftingLogic;
    @Inject(method = "<init>", at = @At("TAIL"))
    private void ae2lt$installCompatibleLogic(BlockPos min, BlockPos max, CallbackInfo ci) {
        craftingLogic = new GtoCompatibleCraftingCpuLogic((CraftingCPUCluster) (Object) this, craftingLogic);
    }
}
