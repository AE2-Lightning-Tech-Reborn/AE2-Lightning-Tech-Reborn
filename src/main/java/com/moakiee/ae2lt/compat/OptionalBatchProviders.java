package com.moakiee.ae2lt.compat;

import com.moakiee.ae2lt.compat.useless.UselessBatchCompat;
import com.moakiee.ae2lt.compat.neoeco.NeoEcoBatchCompat;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapter;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderResolver;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import appeng.api.networking.crafting.ICraftingProvider;

/** Optional provider capabilities retain their own resolution cache policies. */
public final class OptionalBatchProviders {
    private OptionalBatchProviders() {
    }

    public static BatchProviderAdapter createAdapter() {
        return combine(NeoEcoBatchCompat.createAdapter(), UselessBatchCompat.createAdapter());
    }

    static BatchProviderResolver combine(BatchProviderResolver first, BatchProviderResolver second) {
        if (first == null) return second;
        if (second == null) return first;
        return new BatchProviderResolver() {
            @Override public IBatchCraftingProvider resolve(ICraftingProvider provider) {
                var result = first.resolve(provider);
                return result == null ? second.resolve(provider) : result;
            }
            @Override public boolean cacheResolutionForTick() {
                return first.cacheResolutionForTick() && second.cacheResolutionForTick();
            }
            @Override public boolean cacheResolutionAcrossTicks() {
                return first.cacheResolutionAcrossTicks() && second.cacheResolutionAcrossTicks();
            }
        };
    }
}
