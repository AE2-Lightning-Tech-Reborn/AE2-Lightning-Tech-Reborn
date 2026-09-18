package com.moakiee.ae2lt.compat;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.networking.crafting.ICraftingProvider;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapter;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderResolver;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import org.junit.jupiter.api.Test;

class OptionalBatchProvidersTest {
    @Test
    void compositionPreservesBothResolversCacheRestrictions() {
        for (boolean tick : new boolean[] {false, true}) {
            for (boolean persistent : new boolean[] {false, true}) {
                for (boolean first : new boolean[] {false, true}) {
                    var restricted = resolver(tick, persistent);
                    var stable = resolver(true, true);
                    var combined = (BatchProviderResolver) OptionalBatchProviders.combine(
                            first ? restricted : stable, first ? stable : restricted);
                    assertEquals(tick, combined.cacheResolutionForTick());
                    assertEquals(persistent, combined.cacheResolutionAcrossTicks());
                }
            }
        }
    }

    @Test
    void legacyJobDependentAdapterRemainsUncachedAndReceivesTheCurrentContext() {
        var stable = resolver(true, true);
        int[] calls = {0};
        BatchProviderAdapter legacy = (provider, pattern, job) -> { calls[0]++; return null; };
        var combined = OptionalBatchProviders.combine(stable, legacy);
        assertFalse(combined instanceof BatchProviderResolver);
        combined.adapt(null, null, null);
        combined.adapt(null, null, null);
        assertEquals(2, calls[0]);
        assertSame(stable, OptionalBatchProviders.combine(stable, null));
        assertSame(legacy, OptionalBatchProviders.combine(null, legacy));
    }

    private static BatchProviderResolver resolver(boolean tick, boolean persistent) {
        return new BatchProviderResolver() {
            @Override public IBatchCraftingProvider resolve(ICraftingProvider provider) { return null; }
            @Override public boolean cacheResolutionForTick() { return tick; }
            @Override public boolean cacheResolutionAcrossTicks() { return persistent; }
        };
    }
}
