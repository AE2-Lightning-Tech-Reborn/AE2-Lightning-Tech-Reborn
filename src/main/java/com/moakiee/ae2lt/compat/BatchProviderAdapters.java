package com.moakiee.ae2lt.compat;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapter;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class BatchProviderAdapters {
    private static final LinkedHashMap<ResourceLocation, BatchProviderAdapter> ADAPTERS = new LinkedHashMap<>();
    private static volatile List<BatchProviderAdapter> snapshot = List.of();
    @Nullable private static volatile CachedAdapter cachedAdapter;

    private BatchProviderAdapters() {
    }

    public static synchronized void register(ResourceLocation id, BatchProviderAdapter adapter) {
        ADAPTERS.put(Objects.requireNonNull(id), Objects.requireNonNull(adapter));
        snapshot = List.copyOf(ADAPTERS.values());
    }

    public static synchronized void unregister(ResourceLocation id) {
        ADAPTERS.remove(id);
        snapshot = List.copyOf(ADAPTERS.values());
    }

    @Nullable
    static IBatchCraftingProvider adapt(ICraftingProvider provider, IPatternDetails pattern, BatchJobView job) {
        for (var adapter : snapshot) {
            var adapted = adapter.adapt(provider, pattern, job);
            if (adapted != null) return adapted;
        }
        return null;
    }

    @Nullable
    public static BatchProviderAdapter withRegistered(@Nullable BatchProviderAdapter fallback) {
        if (snapshot.isEmpty()) return fallback;
        var cached = cachedAdapter;
        if (cached != null && cached.fallback() == fallback) return cached.wrapper();
        synchronized (BatchProviderAdapters.class) {
            if (snapshot.isEmpty()) return fallback;
            cached = cachedAdapter;
            if (cached != null && cached.fallback() == fallback) return cached.wrapper();
            var selectedFallback = fallback;
            BatchProviderAdapter wrapper = (provider, pattern, job) -> {
                var adapted = adapt(provider, pattern, job);
                return adapted != null
                        ? adapted
                        : selectedFallback == null ? null : selectedFallback.adapt(provider, pattern, job);
            };
            cachedAdapter = new CachedAdapter(fallback, wrapper);
            return wrapper;
        }
    }

    private record CachedAdapter(@Nullable BatchProviderAdapter fallback, BatchProviderAdapter wrapper) {
    }
}
