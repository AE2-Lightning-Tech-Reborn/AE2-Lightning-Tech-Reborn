package com.moakiee.ae2lt.mixin;

import appeng.helpers.patternprovider.PatternProviderLogic;
import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationMutationScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternMigrationProviderBatchMixin {
    @Shadow public abstract void updatePatterns();

    @Inject(method = "updatePatterns", at = @At("HEAD"), cancellable = true)
    private void ae2lt$batchCatalogRefresh(CallbackInfo ci) {
        if (PatternMigrationMutationScope.defer(this, this::updatePatterns)) ci.cancel();
    }
}
