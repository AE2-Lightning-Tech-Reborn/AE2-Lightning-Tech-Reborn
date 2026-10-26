package com.moakiee.ae2lt.logic.craft.migration;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraftforge.fml.ModList;

/** Coordinates mutations with NeoECO without linking optional classes into ordinary startup. */
final class EcoMigrationLease implements AutoCloseable {
    private final Object coordinator, owner;
    private final Method release, isOwner;
    private boolean closed;

    private EcoMigrationLease(Object coordinator, Object owner, Method release, Method isOwner) {
        this.coordinator = coordinator; this.owner = owner; this.release = release; this.isOwner = isOwner;
    }

    static EcoMigrationLease acquire(Object grid, Object owner) {
        var mods = ModList.get();
        if (mods == null || !mods.isLoaded("neoecoae")) return new EcoMigrationLease(null, owner, null, null);
        try {
            return acquire(Class.forName("cn.dancingsnow.neoecoae.grid.PatternMigrationCoordinator"), grid, owner);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("NeoECO migration coordinator unavailable", failure);
        }
    }

    static EcoMigrationLease acquire(Class<?> type, Object grid, Object owner) throws ReflectiveOperationException {
        Method factory = type.getMethod("forGrid", Object.class);
        Method acquire = type.getMethod("tryAcquire", Object.class);
        Method release = type.getMethod("release", Object.class);
        Method isOwner = type.getMethod("isOwner", Object.class);
        if (!Modifier.isStatic(factory.getModifiers()) || factory.getReturnType() != type
                || Modifier.isStatic(acquire.getModifiers()) || acquire.getReturnType() != boolean.class
                || Modifier.isStatic(release.getModifiers()) || release.getReturnType() != void.class
                || Modifier.isStatic(isOwner.getModifiers()) || isOwner.getReturnType() != boolean.class) {
            throw new NoSuchMethodException("Incompatible NeoECO migration coordinator");
        }
        Object coordinator = factory.invoke(null, grid);
        if (!(boolean) acquire.invoke(coordinator, owner)) return null;
        return new EcoMigrationLease(coordinator, owner, release, isOwner);
    }

    boolean owned() {
        if (closed) return false;
        if (coordinator == null) return true;
        try { return (boolean) isOwner.invoke(coordinator, owner); }
        catch (ReflectiveOperationException failure) { return false; }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (coordinator == null) return;
        try { release.invoke(coordinator, owner); }
        catch (ReflectiveOperationException failure) {
            appeng.core.AELog.warn("[ae2lt] Could not release NeoECO migration lease. %s", failure);
        }
    }
}
