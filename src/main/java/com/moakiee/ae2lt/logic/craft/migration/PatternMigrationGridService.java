package com.moakiee.ae2lt.logic.craft.migration;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;

/** Incremental node roster: a task freezes an epoch, without copying a large grid in its start packet. */
public final class PatternMigrationGridService implements IGridService, IGridServiceProvider {
    private final Map<Class<?>, NavigableMap<Long, IGridNode>> byClass = new IdentityHashMap<>();
    private final Map<IGridNode, Long> ids = new IdentityHashMap<>();
    private final Map<Object, java.util.NavigableSet<Long>> ownerIds = new IdentityHashMap<>();
    private long epoch;

    public PatternMigrationGridService() {}

    @Override public void addNode(IGridNode node, CompoundTag savedData) {
        if (ids.containsKey(node)) return;
        long id = ++epoch;
        ids.put(node, id);
        ownerIds.computeIfAbsent(node.getOwner(), ignored -> new java.util.TreeSet<>()).add(id);
        byClass.computeIfAbsent(node.getOwner().getClass(), ignored -> new TreeMap<>()).put(id, node);
    }

    @Override public void removeNode(IGridNode node) {
        Long id = ids.remove(node);
        if (id == null) return;
        var ownerNodes = ownerIds.get(node.getOwner());
        if (ownerNodes != null) {
            ownerNodes.remove(id);
            if (ownerNodes.isEmpty()) ownerIds.remove(node.getOwner());
        }
        var nodes = byClass.get(node.getOwner().getClass());
        if (nodes != null) {
            nodes.remove(id);
            if (nodes.isEmpty()) byClass.remove(node.getOwner().getClass());
        }
    }

    public long epoch() { return epoch; }

    public boolean wasPresent(Object owner, long snapshotEpoch) {
        var nodes = ownerIds.get(owner);
        return nodes != null && nodes.first() <= snapshotEpoch;
    }

    public Entry next(Class<?> concreteClass, long after, long snapshotEpoch) {
        var nodes = byClass.get(concreteClass);
        if (nodes == null) return null;
        var entry = nodes.higherEntry(after);
        return entry == null || entry.getKey() > snapshotEpoch ? null : new Entry(entry.getKey(), entry.getValue());
    }

    public record Entry(long id, IGridNode node) {}
}
