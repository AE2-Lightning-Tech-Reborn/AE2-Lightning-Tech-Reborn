package com.moakiee.ae2lt.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.IStorageProvider;
import appeng.me.storage.NetworkStorage;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationRefundScope;
import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationStorageOrigins;
import java.util.IdentityHashMap;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkStorage.class, remap = false)
public abstract class PatternMigrationRefundMixin implements PatternMigrationStorageOrigins {
    @Unique private final Map<MEStorage, IStorageProvider> ae2lt$migrationOrigins = new IdentityHashMap<>();

    @Override public void ae2lt$registerMigrationStorage(MEStorage inventory, IStorageProvider provider) {
        ae2lt$migrationOrigins.put(inventory, provider);
    }

    @Inject(method = "unmount", at = @At("HEAD"))
    private void ae2lt$forgetStorageOrigin(MEStorage inventory, CallbackInfo ci) {
        ae2lt$migrationOrigins.remove(inventory);
    }
    @WrapOperation(method = "insert", at = @At(value = "INVOKE",
            target = "Lappeng/api/storage/MEStorage;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J"))
    private long ae2lt$refundIntoSafeStorage(MEStorage inventory, AEKey key, long amount,
            Actionable mode, IActionSource source, Operation<Long> original) {
        return PatternMigrationRefundScope.insert(inventory, ae2lt$migrationOrigins.get(inventory), key, amount, mode, source,
                () -> original.call(inventory, key, amount, mode, source));
    }
}
