package com.moakiee.ae2lt.compat;

import com.moakiee.ae2lt.compat.useless.UselessBatchCompat;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapter;

/** Supplies the optional Useless resolver with its original cache policy. */
public final class OptionalBatchProviders {
    private OptionalBatchProviders() {
    }

    public static BatchProviderAdapter createAdapter() {
        return UselessBatchCompat.createAdapter();
    }
}
