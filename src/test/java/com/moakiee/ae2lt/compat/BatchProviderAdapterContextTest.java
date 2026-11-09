package com.moakiee.ae2lt.compat;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapter;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderResolver;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import com.moakiee.thunderbolt.core.crafting.support.CraftingProviderRevision;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BatchProviderAdapterContextTest {
    private static final ResourceLocation LOW = id("low");
    private static final ResourceLocation FIRST = id("a");
    private static final ResourceLocation SECOND = id("z");
    private static final ResourceLocation LOCAL = id("local");

    @AfterEach
    void cleanRegistrations() {
        for (var id : List.of(LOW, FIRST, SECOND, LOCAL)) {
            com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapters.unregister(id);
            BatchProviderAdapters.unregister(id);
        }
    }

    @Test
    void composesLocalGlobalsAndFallbackWithNativePrecedence() {
        var local = new Endpoint();
        var global = new Endpoint();
        var fallback = new Endpoint();
        var firstCalls = new AtomicInteger();
        var secondCalls = new AtomicInteger();
        register(LOW, -20, (p, pattern, job) -> fallback.value);
        register(SECOND, 10, (p, pattern, job) -> { secondCalls.incrementAndGet(); return fallback.value; });
        register(FIRST, 10, (p, pattern, job) -> { firstCalls.incrementAndGet(); return global.value; });
        var context = new BatchProviderAdapterContext();
        context.beginTick(new Revision(), 10, (p, pattern, job) -> fallback.value);
        var provider = provider();
        assertSame(global.value, context.adapt(provider, null, null));
        assertEquals(1, firstCalls.get());
        assertEquals(0, secondCalls.get(), "Core priority then ID order must be retained");

        BatchProviderAdapters.register(LOCAL, (p, pattern, job) -> local.value);
        assertSame(local.value, context.adapt(provider, null, null));
        assertEquals(1, firstCalls.get());
        var nativeProvider = nativeProvider();
        assertSame(nativeProvider, context.adapt(nativeProvider, null, null));
        BatchProviderAdapters.unregister(LOCAL);
        for (var id : List.of(LOW, FIRST, SECOND)) {
            com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapters.unregister(id);
        }
        assertSame(fallback.value, context.adapt(provider, null, null));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cachesHitsAndMissesByProviderIdentityForTheDeclaredLifetime(boolean acrossTicks) {
        var endpoint = new Endpoint();
        var calls = new AtomicInteger();
        var supported = provider();
        var unsupported = provider();
        register(FIRST, 0, resolver(p -> p == supported ? endpoint.value : null, calls, true, acrossTicks));
        var service = new Revision();
        var context = new BatchProviderAdapterContext();
        context.beginTick(service, 10, null);
        for (int i = 0; i < 3; i++) {
            assertSame(endpoint.value, context.adapt(supported, null, null));
            assertNull(context.adapt(unsupported, null, null));
        }
        assertEquals(2, calls.get());
        assertEquals(List.of(10L), endpoint.ticks);
        context.beginTick(service, 11, null);
        assertSame(endpoint.value, context.adapt(supported, null, null));
        assertNull(context.adapt(unsupported, null, null));
        assertEquals(acrossTicks ? 2 : 4, calls.get());
        assertEquals(List.of(10L, 11L), endpoint.ticks);
        context.beginTick(service, 11, null);
        assertSame(endpoint.value, context.adapt(supported, null, null));
        assertEquals(List.of(10L, 11L), endpoint.ticks, "a physical tick may revisit a virtual CPU");
    }

    @Test
    void patternAndBorrowedJobViewsStayLiveWhileFallbackResolutionIsCached() {
        var seen = new ArrayList<String>();
        var currentJob = new AtomicReference<>("first");
        var job = (BatchJobView) Proxy.newProxyInstance(BatchJobView.class.getClassLoader(),
                new Class<?>[]{BatchJobView.class}, (p, m, args) -> currentJob.get());
        var firstPattern = pattern();
        var secondPattern = pattern();
        register(FIRST, 0, (provider, actualPattern, actualJob) -> {
            assertSame(job, actualJob);
            seen.add(actualJob.toString() + (actualPattern == firstPattern ? ":a" : ":b"));
            return null;
        });
        var calls = new AtomicInteger();
        var endpoint = new Endpoint();
        var context = new BatchProviderAdapterContext();
        context.beginTick(new Revision(), 10, resolver(p -> endpoint.value, calls, true, true));
        var provider = provider();
        assertSame(endpoint.value, context.adapt(provider, firstPattern, job));
        currentJob.set("second");
        assertSame(endpoint.value, context.adapt(provider, secondPattern, job));
        assertEquals(List.of("first:a", "second:b"), seen);
        assertEquals(1, calls.get());
        assertEquals(List.of(10L), endpoint.ticks);
    }

    @Test
    void mixedResolversKeepIndependentTickAndCrossTickPolicies() {
        var tickCalls = new AtomicInteger();
        var stableCalls = new AtomicInteger();
        var plainCalls = new AtomicInteger();
        var endpoint = new Endpoint();
        BatchProviderAdapters.register(LOCAL, (p, pattern, job) -> { plainCalls.incrementAndGet(); return null; });
        register(FIRST, 0, resolver(p -> null, tickCalls, true, false));
        var fallback = resolver(p -> endpoint.value, stableCalls, true, true);
        var service = new Revision();
        var context = new BatchProviderAdapterContext();
        var provider = provider();
        for (long tick : new long[]{10, 11}) {
            context.beginTick(service, tick, fallback);
            for (int i = 0; i < 2; i++) assertSame(endpoint.value, context.adapt(provider, null, null));
        }
        assertEquals(4, plainCalls.get());
        assertEquals(2, tickCalls.get());
        assertEquals(1, stableCalls.get());
        assertEquals(List.of(10L, 11L), endpoint.ticks);
    }

    @Test
    void registrationChangesInvalidateCachedHitsAndMissesWithinTheSameTick() {
        var chosen = new AtomicReference<IBatchCraftingProvider>();
        var calls = new AtomicInteger();
        register(FIRST, 0, resolver(p -> chosen.get(), calls, true, true));
        var context = new BatchProviderAdapterContext();
        context.beginTick(new Revision(), 10, null);
        var provider = provider();
        assertNull(context.adapt(provider, null, null));
        assertNull(context.adapt(provider, null, null));
        assertEquals(1, calls.get());
        var global = new Endpoint();
        chosen.set(global.value);
        register(SECOND, -1, (p, pattern, job) -> null);
        assertSame(global.value, context.adapt(provider, null, null));
        assertEquals(2, calls.get());
        var local = new Endpoint();
        BatchProviderAdapters.register(LOCAL, (p, pattern, job) -> local.value);
        assertSame(local.value, context.adapt(provider, null, null));
        BatchProviderAdapters.unregister(LOCAL);
        assertSame(global.value, context.adapt(provider, null, null));
        assertEquals(3, calls.get());
        com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapters.unregister(FIRST);
        assertNull(context.adapt(provider, null, null));
    }

    @Test
    void providerRevisionAndServiceChangesInvalidateStableResolutionImmediately() {
        var calls = new AtomicInteger();
        var current = new AtomicReference<>(new Endpoint());
        var fallback = resolver(p -> current.get().value, calls, true, true);
        var service = new Revision();
        var context = new BatchProviderAdapterContext();
        var provider = provider();
        context.beginTick(service, 10, fallback);
        assertSame(current.get().value, context.adapt(provider, null, null));
        current.set(new Endpoint());
        service.value++;
        assertSame(current.get().value, context.adapt(provider, null, null));
        assertEquals(2, calls.get());
        context.beginTick(new Revision(), 10, fallback);
        assertSame(current.get().value, context.adapt(provider, null, null));
        assertEquals(3, calls.get());
        var untracked = new Object();
        context.beginTick(untracked, 10, fallback);
        assertSame(current.get().value, context.adapt(provider, null, null));
        context.beginTick(untracked, 11, fallback);
        assertSame(current.get().value, context.adapt(provider, null, null));
        assertEquals(5, calls.get());
    }

    @Test
    void independentCpusDoNotShareResolvedEndpointsOrTickState() {
        var calls = new AtomicInteger();
        var created = new ArrayList<Endpoint>();
        register(FIRST, 0, resolver(p -> { var endpoint = new Endpoint(); created.add(endpoint); return endpoint.value; }, calls, true, true));
        var first = new BatchProviderAdapterContext();
        var second = new BatchProviderAdapterContext();
        var service = new Revision();
        var provider = provider();
        first.beginTick(service, 10, null);
        var a = first.adapt(provider, null, null);
        second.beginTick(service, 11, null);
        var b = second.adapt(provider, null, null);
        assertNotSame(a, b);
        assertSame(a, first.adapt(provider, null, null));
        first.beginTick(service, 12, null);
        assertSame(a, first.adapt(provider, null, null));
        assertEquals(2, calls.get());
        assertEquals(List.of(10L, 12L), created.get(0).ticks);
        assertEquals(List.of(11L), created.get(1).ticks);
    }

    @Test
    void uncachedResolversAreNotMemoized() {
        var calls = new AtomicInteger();
        register(FIRST, 0, resolver(p -> new Endpoint().value, calls, false, false));
        var context = new BatchProviderAdapterContext();
        context.beginTick(new Revision(), 10, null);
        var provider = provider();
        for (int i = 0; i < 4; i++) assertNotNull(context.adapt(provider, null, null));
        assertEquals(4, calls.get());
    }

    private static void register(ResourceLocation id, int priority, BatchProviderAdapter adapter) {
        com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapters.register(id, priority, adapter);
    }
    private static ResourceLocation id(String name) { return new ResourceLocation("ae2lt_context_test", name); }
    private static ICraftingProvider provider() { return proxy(ICraftingProvider.class); }
    private static IPatternDetails pattern() { return proxy(IPatternDetails.class); }
    private static ICraftingProvider nativeProvider() {
        return (ICraftingProvider) Proxy.newProxyInstance(ICraftingProvider.class.getClassLoader(),
                new Class<?>[]{ICraftingProvider.class, IBatchCraftingProvider.class}, (p, m, args) -> null);
    }
    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, args) -> null));
    }
    private static BatchProviderResolver resolver(Function<ICraftingProvider, IBatchCraftingProvider> resolve,
            AtomicInteger calls, boolean forTick, boolean acrossTicks) {
        return new BatchProviderResolver() {
            public IBatchCraftingProvider resolve(ICraftingProvider provider) { calls.incrementAndGet(); return resolve.apply(provider); }
            public boolean cacheResolutionForTick() { return forTick; }
            public boolean cacheResolutionAcrossTicks() { return acrossTicks; }
        };
    }
    private static final class Revision implements CraftingProviderRevision {
        private long value;
        public long thunderbolt$getCraftingProviderRevision() { return value; }
    }
    private static final class Endpoint {
        private final List<Long> ticks = new ArrayList<>();
        private final IBatchCraftingProvider value = (IBatchCraftingProvider) Proxy.newProxyInstance(
                IBatchCraftingProvider.class.getClassLoader(), new Class<?>[]{IBatchCraftingProvider.class},
                (p, m, args) -> { if (m.getName().equals("beginDispatchTick")) ticks.add((Long) args[0]); return null; });
    }
}
