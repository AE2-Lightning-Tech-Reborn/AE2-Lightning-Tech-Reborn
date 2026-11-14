package com.moakiee.ae2lt.compat;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapter;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderResolver;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import com.moakiee.thunderbolt.core.crafting.support.CraftingProviderRevision;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Physical-CPU-owned adapter composition: local definitions, Core globals, then the optional
 * fallback. Each resolver keeps its own lifetime; pattern/job adapters always receive live input.
 */
public final class BatchProviderAdapterContext implements BatchProviderAdapter {
    private static final int MAX_RESOLVERS = 128;
    private static final int MAX_PROVIDERS = 512;
    private final Reference2ObjectLinkedOpenHashMap<BatchProviderResolver,
            Reference2ObjectLinkedOpenHashMap<ICraftingProvider, Resolution>> resolutions =
            new Reference2ObjectLinkedOpenHashMap<>();
    private List<BatchProviderAdapter> locals;
    private List<com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapters.Entry> globals;
    private Object service;
    private long providerRevision;
    private long tick;
    private boolean started;
    @Nullable private BatchProviderAdapter fallback;

    /** Share one context across all virtual CPUs scheduled by a physical CPU. */
    public void beginTick(Object service, long tick, @Nullable BatchProviderAdapter fallback) {
        if (this.service != service || this.fallback != fallback) resolutions.clear();
        if (started && this.tick != tick) {
            resolutions.keySet().removeIf(resolver -> !resolver.cacheResolutionAcrossTicks());
        }
        this.service = service;
        this.fallback = fallback;
        this.tick = tick;
        started = true;
        refresh();
    }

    private void refresh() {
        var localSnapshot = BatchProviderAdapters.snapshot();
        var globalSnapshot = com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapters.entries();
        long revision = service instanceof CraftingProviderRevision tracked
                ? tracked.thunderbolt$getCraftingProviderRevision() : tick;
        if (locals != localSnapshot || globals != globalSnapshot || providerRevision != revision) {
            resolutions.clear();
            locals = localSnapshot;
            globals = globalSnapshot;
            providerRevision = revision;
        }
    }

    @Override
    public @Nullable IBatchCraftingProvider adapt(
            ICraftingProvider provider, IPatternDetails pattern, BatchJobView job) {
        if (!started) throw new IllegalStateException("Adapter context requires beginTick");
        if (provider instanceof IBatchCraftingProvider nativeProvider) return nativeProvider;
        refresh();
        // Registrations are immutable snapshots, including changes made by an adapter callback.
        var localSnapshot = locals;
        var globalSnapshot = globals;
        for (var adapter : localSnapshot) {
            var endpoint = resolve(adapter, provider, pattern, job);
            if (endpoint != null) return endpoint;
        }
        for (var entry : globalSnapshot) {
            // Keep the GTL branch's excluded integration out of the newly bridged registry.
            if (entry.id().getNamespace().equals("neoecoae")
                    || entry.id().getNamespace().equals("thunderbolt")
                    && entry.id().getPath().equals("neoecoae")) continue;
            var endpoint = resolve(entry.adapter(), provider, pattern, job);
            if (endpoint != null) return endpoint;
        }
        return fallback == null ? null : resolve(fallback, provider, pattern, job);
    }

    private @Nullable IBatchCraftingProvider resolve(BatchProviderAdapter adapter,
            ICraftingProvider provider, IPatternDetails pattern, BatchJobView job) {
        if (!(adapter instanceof BatchProviderResolver resolver)) {
            return adapter.adapt(provider, pattern, job);
        }
        if (!resolver.cacheResolutionForTick()) return resolver.resolve(provider);
        var providers = resolutions.getAndMoveToLast(resolver);
        if (providers == null) {
            if (resolutions.size() >= MAX_RESOLVERS) resolutions.removeFirst();
            providers = new Reference2ObjectLinkedOpenHashMap<>();
            resolutions.put(resolver, providers);
        }
        var resolution = providers.getAndMoveToLast(provider);
        if (resolution == null) {
            resolution = new Resolution(resolver.resolve(provider));
            if (providers.size() >= MAX_PROVIDERS) providers.removeFirst();
            providers.put(provider, resolution);
        }
        if (!resolution.initialized || resolution.tick != tick) {
            if (resolution.endpoint != null) resolution.endpoint.beginDispatchTick(tick);
            resolution.tick = tick;
            resolution.initialized = true;
        }
        return resolution.endpoint;
    }

    private static final class Resolution {
        private final @Nullable IBatchCraftingProvider endpoint;
        private long tick;
        private boolean initialized;
        private Resolution(@Nullable IBatchCraftingProvider endpoint) { this.endpoint = endpoint; }
    }
}
