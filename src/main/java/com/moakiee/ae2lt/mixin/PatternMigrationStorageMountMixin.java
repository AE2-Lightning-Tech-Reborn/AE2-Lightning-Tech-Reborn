package com.moakiee.ae2lt.mixin;

import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationStorageOrigins;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "appeng.me.service.StorageService$ProviderState", remap = false)
public abstract class PatternMigrationStorageMountMixin {
    @Shadow @Final private IStorageProvider provider;

    @WrapOperation(method = "mount(Lappeng/api/storage/MEStorage;I)V", at = @At(value = "INVOKE",
            target = "Lappeng/me/storage/NetworkStorage;mount(ILappeng/api/storage/MEStorage;)V"))
    private void ae2lt$recordStorageOrigin(NetworkStorage network, int priority, MEStorage inventory,
            Operation<Void> original) {
        ((PatternMigrationStorageOrigins) network).ae2lt$registerMigrationStorage(inventory, provider);
        original.call(network, priority, inventory);
    }
}
