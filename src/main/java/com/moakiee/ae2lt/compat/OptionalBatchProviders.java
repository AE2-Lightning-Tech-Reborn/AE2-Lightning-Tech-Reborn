package com.moakiee.ae2lt.compat;

import appeng.api.networking.crafting.ICraftingProvider;
import com.moakiee.ae2lt.compat.neoeco.NeoEcoFastPathCompat;
import com.moakiee.ae2lt.compat.useless.UselessBatchCompat;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapter;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderResolver;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;

/** Preserves both optional resolvers' cache lifetimes and dynamic opt-outs. */
public final class OptionalBatchProviders {
    private OptionalBatchProviders() {
    }

    public static BatchProviderAdapter createAdapter() {
        return combine(UselessBatchCompat.createAdapter(), NeoEcoFastPathCompat.createAdapter());
    }

    static BatchProviderAdapter combine(BatchProviderResolver useless, BatchProviderAdapter neoeco) {
        if (useless == null) return neoeco;
        if (neoeco == null) return useless;
        if (neoeco instanceof BatchProviderResolver resolver) {
            return new BatchProviderResolver() {
                @Override
                public IBatchCraftingProvider resolve(ICraftingProvider provider) {
                    var adapted = useless.resolve(provider);
                    return adapted != null ? adapted : resolver.resolve(provider);
                }

                @Override
                public boolean cacheResolutionForTick() {
                    return useless.cacheResolutionForTick() && resolver.cacheResolutionForTick();
                }

                @Override
                public boolean cacheResolutionAcrossTicks() {
                    return useless.cacheResolutionAcrossTicks() && resolver.cacheResolutionAcrossTicks();
                }
            };
        }
        return (provider, pattern, job) -> {
            var adapted = useless.resolve(provider);
            return adapted != null ? adapted : neoeco.adapt(provider, pattern, job);
        };
    }
}
