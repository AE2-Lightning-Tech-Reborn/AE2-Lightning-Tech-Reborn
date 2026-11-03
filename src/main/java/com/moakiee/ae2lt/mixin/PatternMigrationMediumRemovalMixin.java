package com.moakiee.ae2lt.mixin;

import appeng.api.networking.crafting.ICraftingProvider;
import com.google.common.collect.Iterators;
import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationProviderRefreshScope;
import java.util.Iterator;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "appeng.me.service.helpers.NetworkCraftingProviders$CraftingProviderList", remap = false)
public abstract class PatternMigrationMediumRemovalMixin {
    @Shadow @Final private List<ICraftingProvider> providers;
    @Shadow private Iterator<ICraftingProvider> cycleIterator;

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void ae2lt$compactWholeProvider(ICraftingProvider provider, CallbackInfo ci) {
        if (PatternMigrationProviderRefreshScope.remove(this, provider, providers,
                () -> cycleIterator = Iterators.cycle(providers))) ci.cancel();
    }
}
