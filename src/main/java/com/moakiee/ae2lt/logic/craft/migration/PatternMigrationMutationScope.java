package com.moakiee.ae2lt.logic.craft.migration;

import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;

/** Defers only catalog refreshes; physical inventory writes and dirty flags remain synchronous. */
public final class PatternMigrationMutationScope implements AutoCloseable {
    private static final ThreadLocal<PatternMigrationMutationScope> CURRENT = new ThreadLocal<>();
    private final IdentityHashMap<Object, Boolean> owners = new IdentityHashMap<>();
    private final List<Runnable> refreshes = new ArrayList<>();

    private PatternMigrationMutationScope() { CURRENT.set(this); }

    public static PatternMigrationMutationScope open() {
        if (CURRENT.get() != null) throw new IllegalStateException("Recursive pattern migration");
        return new PatternMigrationMutationScope();
    }

    public static boolean defer(Object owner, Runnable refresh) {
        var scope = CURRENT.get();
        if (scope == null) return false;
        if (scope.owners.put(owner, Boolean.TRUE) == null) scope.refreshes.add(refresh);
        return true;
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
