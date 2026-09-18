package com.moakiee.ae2lt.compat;

import com.moakiee.ae2lt.compat.neoeco.NeoEcoFastPathCompat;
import com.moakiee.ae2lt.compat.useless.UselessBatchCompat;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapter;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderResolver;

/** Retains the executor's per-tick capability cache for optional integrations. */
public final class OptionalBatchProviders {
    private OptionalBatchProviders() {
    }

    public static BatchProviderAdapter createAdapter() {
        var useless = UselessBatchCompat.createAdapter();
        var neoeco = NeoEcoFastPathCompat.createAdapter();
        if (useless == null) return neoeco;
        if (neoeco == null) return useless;
        if (neoeco instanceof BatchProviderResolver resolver) {
            return (BatchProviderResolver) provider -> {
                var adapted = useless.resolve(provider);
                return adapted != null ? adapted : resolver.resolve(provider);
            };
        }
        return (provider, pattern, job) -> {
            var adapted = useless.resolve(provider);
            return adapted != null ? adapted : neoeco.adapt(provider, pattern, job);
        };
    }
}
