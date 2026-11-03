package com.moakiee.ae2lt.client.compat;

import appeng.api.stacks.AEKey;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.IClientRepo;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.crafting.Ingredient;

/** Preserves the live repository across GTO's Set-to-Collection return-type change. */
public final class ClientRepoAccess {
    private static final MethodHandle ALL_ENTRIES = resolveAllEntries();

    private ClientRepoAccess() { }

    private static MethodHandle resolveAllEntries() {
        try {
            // Dev23 returns Set; Dev25 returns Collection. Discover the actual descriptor once.
            return MethodHandles.publicLookup().unreflect(IClientRepo.class.getMethod("getAllEntries"))
                    .asType(MethodType.methodType(Collection.class, IClientRepo.class));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Installed AE2 client repository API changed", failure);
        }
    }

    @SuppressWarnings("unchecked")
    public static Collection<GridInventoryEntry> allEntries(IClientRepo repo) {
        try {
            return (Collection<GridInventoryEntry>) ALL_ENTRIES.invokeExact(repo);
        } catch (Throwable failure) {
            throw new IllegalStateException("Cannot read installed AE2 client repository", failure);
        }
    }

    public static IClientRepo trackUpdates(IClientRepo repo, Runnable beforeUpdate) {
        return new TrackingRepo(repo, beforeUpdate);
    }

    /** Makes javac emit the Collection descriptor as well as the older covariant Set method. */
    private interface CollectionEntries {
        Collection<GridInventoryEntry> getAllEntries();
    }

    private static final class TrackingRepo implements IClientRepo, CollectionEntries {
        private final IClientRepo delegate;
        private final Runnable beforeUpdate;
        private final Set<GridInventoryEntry> entries;

        TrackingRepo(IClientRepo delegate, Runnable beforeUpdate) {
            this.delegate = delegate;
            this.beforeUpdate = beforeUpdate;
            this.entries = liveEntries(allEntries(delegate));
        }

        @Override
        public void handleUpdate(boolean fullUpdate, List<GridInventoryEntry> update) {
            beforeUpdate.run();
            delegate.handleUpdate(fullUpdate, update);
        }

        @Override
        public Set<AEKey> getCraftableKeys() {
            return delegate.getCraftableKeys();
        }

        @Override
        public GridInventoryEntry getByKey(AEKey key) {
            return delegate.getByKey(key);
        }

        @Override
        public Set<GridInventoryEntry> getAllEntries() {
            return entries;
        }

        @Override
        public Collection<GridInventoryEntry> getByIngredient(Ingredient ingredient) {
            return delegate.getByIngredient(ingredient);
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<GridInventoryEntry> liveEntries(Collection<GridInventoryEntry> entries) {
        if (entries instanceof Set<?> set) return (Set<GridInventoryEntry>) set;
        // Both supported repos own one distinct GridInventoryEntry per serial. Dev25 exposes
        // its long-map values instead of a BiMap set; preserve that live view without copying.
        return new AbstractSet<>() {
            @Override
            public Iterator<GridInventoryEntry> iterator() {
                return entries.iterator();
            }

            @Override
            public int size() {
                return entries.size();
            }

            @Override
            public boolean contains(Object value) {
                return entries.contains(value);
            }
        };
    }
}
