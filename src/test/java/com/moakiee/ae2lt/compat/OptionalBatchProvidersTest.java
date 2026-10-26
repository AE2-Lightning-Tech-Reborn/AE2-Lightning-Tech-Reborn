package com.moakiee.ae2lt.compat;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.networking.crafting.ICraftingProvider;
import com.moakiee.thunderbolt.api.crafting.batch.*;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

class OptionalBatchProvidersTest {
    private static IBatchCraftingProvider endpoint() {
        return (IBatchCraftingProvider) Proxy.newProxyInstance(IBatchCraftingProvider.class.getClassLoader(),
                new Class<?>[]{IBatchCraftingProvider.class}, (p,m,a) -> null);
    }
    @Test void absentResolverRetainsThePresentResolversIdentityAndPolicy() {
        var resolver = resolver(null, false);
        assertSame(resolver, OptionalBatchProviders.combine(null, resolver));
        assertSame(resolver, OptionalBatchProviders.combine(resolver, null));
        assertNull(OptionalBatchProviders.combine(null, null));
    }
    @Test void firstSupportedCapabilityWinsAndUnrelatedProvidersFallThrough() {
        var first = endpoint(); var second = endpoint();
        assertSame(first, OptionalBatchProviders.combine(resolver(first, true), resolver(second, true)).resolve(null));
        assertSame(second, OptionalBatchProviders.combine(resolver(null, true), resolver(second, true)).resolve(null));
    }
    @Test void combinedCacheCannotOutliveEitherResolver() {
        var combined = OptionalBatchProviders.combine(resolver(null, true), resolver(null, false));
        assertFalse(combined.cacheResolutionForTick());
        assertFalse(combined.cacheResolutionAcrossTicks());
    }
    private static BatchProviderResolver resolver(IBatchCraftingProvider endpoint, boolean cache) {
        return new BatchProviderResolver() {
            public IBatchCraftingProvider resolve(ICraftingProvider provider) { return endpoint; }
            public boolean cacheResolutionForTick() { return cache; }
            public boolean cacheResolutionAcrossTicks() { return cache; }
        };
    }
}
