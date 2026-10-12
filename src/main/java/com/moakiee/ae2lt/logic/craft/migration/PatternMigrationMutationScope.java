package com.moakiee.ae2lt.logic.craft.migration;

import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;

/** Defers only catalog refreshes; physical inventory writes and dirty flags remain synchronous. */
public final class PatternMigrationMutationScope implements AutoCloseable {
    private static final ThreadLocal<PatternMigrationMutationScope> CURRENT = new ThreadLocal<>();
    private final IdentityHashMap<Object, Boolean> owners = new IdentityHashMap<>();
    private final List<Runnable> refreshes = new ArrayList<>();
    private final IdentityHashMap<Object, PatternMigrationProviderCatalog> catalogs = new IdentityHashMap<>();

    private PatternMigrationMutationScope() { CURRENT.set(this); }

    public static PatternMigrationMutationScope open() {
        if (CURRENT.get() != null) throw new IllegalStateException("Recursive pattern migration");
        return new PatternMigrationMutationScope();
    }

    public static boolean defer(Object owner, Runnable refresh) {
        var scope = CURRENT.get();
        if (scope == null) return false;
        if (scope.owners.put(owner, Boolean.TRUE) == null) {
            var catalog = scope.catalogs.get(owner);
            scope.refreshes.add(catalog != null ? catalog::flush : refresh);
        }
        return true;
    }

    void track(PatternMigrationProviderCatalog catalog, int slot, appeng.api.stacks.AEItemKey key) {
        catalogs.put(catalog.owner, catalog);
        catalog.track(slot, key);
    }

    public static void inventoryChanged(Object owner, int slot) {
        var scope = CURRENT.get();
        if (scope != null) {
            var catalog = scope.catalogs.get(owner);
            if (catalog != null) catalog.changed(slot);
        }
    }

    /** Optional mixins must not shadow a third-party method that may be absent in another version. */
    public static boolean deferOptional(Object owner, String method) {
        return defer(owner, () -> MigrationReflection.call(owner, method));
    }

    @Override public void close() {
        CURRENT.remove();
        RuntimeException failure = null;
        for (var refresh : refreshes) {
            try { refresh.run(); }
            catch (RuntimeException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        }
        if (failure != null) throw failure;
    }
}
