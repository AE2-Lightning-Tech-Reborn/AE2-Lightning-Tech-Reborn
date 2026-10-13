package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationMutationScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixCluster", remap = false)
public abstract class PatternMigrationEaepBatchMixin {
    @Inject(method = "refreshCraftingProvider", at = @At("HEAD"), cancellable = true, require = 0)
    private void ae2lt$batchCatalogRefresh(CallbackInfo ci) {
        if (PatternMigrationMutationScope.deferOptional(this, "refreshCraftingProvider")) ci.cancel();
    }
}
