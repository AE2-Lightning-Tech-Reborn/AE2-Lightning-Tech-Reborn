package com.moakiee.ae2lt.compat;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.networking.crafting.ICraftingProvider;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderResolver;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BatchProviderAdaptersTest {
    private static final ResourceLocation ADAPTER_ID = new ResourceLocation("ae2lt", "registry_test");

    @AfterEach
    void unregisterAdapter() {
        BatchProviderAdapters.unregister(ADAPTER_ID);
    }

    @Test
    void emptyRegistryPreservesResolverIdentityAndCachePolicy() {
        var fallback = resolver(endpoint(), new AtomicInteger());
        assertSame(fallback, BatchProviderAdapters.withRegistered(fallback));
        assertTrue(fallback.cacheResolutionForTick());
        assertTrue(fallback.cacheResolutionAcrossTicks());
        assertNull(BatchProviderAdapters.withRegistered(null));
    }

    @Test
    void registeredAdapterTakesPriorityAndUnregisterRestoresFallback() {
        var registeredEndpoint = endpoint();
        var fallbackEndpoint = endpoint();
        var fallbackCalls = new AtomicInteger();
        var fallback = resolver(fallbackEndpoint, fallbackCalls);
        BatchProviderAdapters.register(ADAPTER_ID, (provider, pattern, job) -> registeredEndpoint);
        var combined = BatchProviderAdapters.withRegistered(fallback);
        assertSame(combined, BatchProviderAdapters.withRegistered(fallback));
        assertFalse(combined instanceof BatchProviderResolver);
        assertSame(registeredEndpoint, combined.adapt(null, null, null));
        assertEquals(0, fallbackCalls.get());

        BatchProviderAdapters.unregister(ADAPTER_ID);
        assertSame(fallbackEndpoint, combined.adapt(null, null, null));
        assertEquals(1, fallbackCalls.get());
        assertSame(fallback, BatchProviderAdapters.withRegistered(fallback));
    }

    @Test
    void rejectedAdapterFallsBackAndReplacementIsVisibleToExistingDispatch() {
        var registeredEndpoint = endpoint();
        var fallbackEndpoint = endpoint();
        var fallbackCalls = new AtomicInteger();
        BatchProviderAdapters.register(ADAPTER_ID, (provider, pattern, job) -> null);
        var combined = BatchProviderAdapters.withRegistered(resolver(fallbackEndpoint, fallbackCalls));
        assertSame(fallbackEndpoint, combined.adapt(null, null, null));
        assertSame(fallbackEndpoint, combined.adapt(null, null, null));
        assertEquals(2, fallbackCalls.get());

        BatchProviderAdapters.register(ADAPTER_ID, (provider, pattern, job) -> registeredEndpoint);
        assertSame(registeredEndpoint, combined.adapt(null, null, null));
        assertEquals(2, fallbackCalls.get());
        assertSame(registeredEndpoint, BatchProviderAdapters.withRegistered(null).adapt(null, null, null));
    }

    private static BatchProviderResolver resolver(IBatchCraftingProvider endpoint, AtomicInteger calls) {
        return new BatchProviderResolver() {
            @Override
            public IBatchCraftingProvider resolve(ICraftingProvider provider) {
                calls.incrementAndGet();
                return endpoint;
            }

            @Override
            public boolean cacheResolutionForTick() {
                return true;
            }

            @Override
            public boolean cacheResolutionAcrossTicks() {
                return true;
            }
        };
    }

    @Test
    void concurrentFallbacksKeepTheirOwnEndpoints() throws Exception {
        BatchProviderAdapters.register(ADAPTER_ID, (provider, pattern, job) -> null);
        var workers = Executors.newFixedThreadPool(4);
        var ready = new CountDownLatch(4);
        var start = new CountDownLatch(1);
        var results = new ArrayList<Future<?>>();
        try {
            for (int worker = 0; worker < 4; worker++) {
                var expected = endpoint();
                var fallback = resolver(expected, new AtomicInteger());
                results.add(workers.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    for (int attempt = 0; attempt < 20_000; attempt++) {
                        assertSame(expected, BatchProviderAdapters.withRegistered(fallback).adapt(null, null, null));
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (var result : results) result.get(20, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static IBatchCraftingProvider endpoint() {
        return (IBatchCraftingProvider) Proxy.newProxyInstance(
                IBatchCraftingProvider.class.getClassLoader(), new Class<?>[] {IBatchCraftingProvider.class},
                (proxy, method, arguments) -> { throw new UnsupportedOperationException(method.getName()); });
    }
}
