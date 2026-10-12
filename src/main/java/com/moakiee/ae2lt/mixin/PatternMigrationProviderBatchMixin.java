package com.moakiee.ae2lt.mixin;

import appeng.helpers.patternprovider.PatternProviderLogic;
import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationMutationScope;
import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationProviderRevision;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternMigrationProviderBatchMixin implements PatternMigrationProviderRevision {
    @Shadow public abstract void updatePatterns();
    @Unique private long ae2lt$catalogRevision;

    @Override public long ae2lt$patternCatalogRevision() { return ae2lt$catalogRevision; }

    @Inject(method = "onChangeInventory", at = @At("HEAD"))
    private void ae2lt$observeInventoryChange(appeng.api.inventories.InternalInventory inventory, int slot, CallbackInfo ci) {
        PatternMigrationMutationScope.inventoryChanged(this, slot);
    }

    @Inject(method = "updatePatterns", at = @At("HEAD"), cancellable = true)
    private void ae2lt$batchCatalogRefresh(CallbackInfo ci) {
        if (PatternMigrationMutationScope.defer(this, this::updatePatterns)) ci.cancel();
        else ae2lt$catalogRevision++;
    }
}
