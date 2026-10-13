package com.moakiee.ae2lt.logic.craft.migration;

import java.lang.reflect.Method;
import net.neoforged.fml.ModList;

/** Shares ECO's lease without a hard class-loading or compile dependency. */
final class EcoMigrationLease implements AutoCloseable {
    private final Object coordinator;
    private final Object owner;
    private final Method release;
    private final Method isOwner;

    private EcoMigrationLease(Object coordinator, Object owner, Method release, Method isOwner) {
        this.coordinator = coordinator;
        this.owner = owner;
        this.release = release;
        this.isOwner = isOwner;
    }

    static EcoMigrationLease acquire(Object grid, Object owner) {
        if (!ModList.get().isLoaded("neoecoae")) return new EcoMigrationLease(null, owner, null, null);
        try {
            Class<?> type = Class.forName("cn.dancingsnow.neoecoae.grid.PatternMigrationCoordinator");
            Object coordinator = type.getMethod("forGrid", Object.class).invoke(null, grid);
            Method release = type.getMethod("release", Object.class);
            Method isOwner = type.getMethod("isOwner", Object.class);
            if (!Boolean.TRUE.equals(type.getMethod("tryAcquire", Object.class).invoke(coordinator, owner))) return null;
            return new EcoMigrationLease(coordinator, owner, release, isOwner);
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("ECO migration lease API unavailable", e);
        }
    }

    boolean owned() {
        if (coordinator == null) return true;
        try { return Boolean.TRUE.equals(isOwner.invoke(coordinator, owner)); }
        catch (ReflectiveOperationException e) { return false; }
    }

    @Override public void close() {
        if (coordinator == null) return;
        try { release.invoke(coordinator, owner); }
        catch (ReflectiveOperationException ignored) { /* The ECO lease itself also weakly references its owner. */ }
    }
}
