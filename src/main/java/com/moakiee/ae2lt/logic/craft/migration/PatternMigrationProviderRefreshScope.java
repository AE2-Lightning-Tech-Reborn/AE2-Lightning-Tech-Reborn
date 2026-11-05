package com.moakiee.ae2lt.logic.craft.migration;

import appeng.api.networking.crafting.ICraftingProvider;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;

/** Batches duplicate medium removals only while a whole native provider is being unmounted. */
public final class PatternMigrationProviderRefreshScope {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private static final Logger LOG = LogUtils.getLogger();
    private static final IdentityHashMap<ICraftingProvider, Runnable> PENDING_REFRESHES =
            new IdentityHashMap<>();
    private record Context(ICraftingProvider provider, Set<Object> lists) {}

    private PatternMigrationProviderRefreshScope() {}

    public static void refresh(ICraftingProvider provider, Runnable refresh) {
        var previous = CURRENT.get();
        CURRENT.set(new Context(provider, Collections.newSetFromMap(new IdentityHashMap<>())));
        try { refresh.run(); }
        finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    /** Catalog updates stay synchronous; coalesce the native medium rebuild at server tick end. */
    public static void enqueue(ICraftingProvider provider, Runnable refresh) {
        PENDING_REFRESHES.put(provider, refresh);
    }

    public static void clear() {
        PENDING_REFRESHES.clear();
        CURRENT.remove();
    }

    /** Called once after all dimensions and controllers have ticked. */
    public static void drain() {
        if (PENDING_REFRESHES.isEmpty()) return;
        var pending = new IdentityHashMap<>(PENDING_REFRESHES);
        PENDING_REFRESHES.clear();
        for (var entry : pending.entrySet()) {
            try {
                refresh(entry.getKey(), entry.getValue());
            } catch (RuntimeException e) {
                LOG.error("Deferred AE2 crafting-provider refresh failed", e);
            }
        }
    }

    public static boolean remove(Object owner, ICraftingProvider provider, List<ICraftingProvider> providers,
            Runnable resetIterator) {
        var context = CURRENT.get();
        if (context == null || context.provider != provider || providers.getClass() != ArrayList.class) return false;
        if (context.lists.add(owner)) {
            // The enclosing native refresh removes every pattern of this provider. One
            // stable compaction replaces repeated ArrayList.remove(0) for duplicate rows.
            providers.removeIf(provider::equals);
            resetIterator.run();
        }
        return true;
    }
}
