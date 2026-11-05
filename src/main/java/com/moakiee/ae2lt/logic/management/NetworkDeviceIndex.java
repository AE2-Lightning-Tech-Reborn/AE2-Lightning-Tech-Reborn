package com.moakiee.ae2lt.logic.management;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Maintained by AE2 topology callbacks; no tick callback and no repeated grid traversal. */
public final class NetworkDeviceIndex implements IGridService, IGridServiceProvider {
    public record Device(UUID id, IGridNode node, DeviceLocation location, boolean active) {}
    private final Reference2ObjectOpenHashMap<IGridNode, UUID> nodes = new Reference2ObjectOpenHashMap<>();
    private List<Device> snapshot = List.of();
    private long nextRefresh = Long.MIN_VALUE;

    @Override public void addNode(IGridNode node, CompoundTag saved) {
        nodes.put(node, UUID.randomUUID()); nextRefresh = Long.MIN_VALUE;
    }
    @Override public void removeNode(IGridNode node) {
        nodes.remove(node); nextRefresh = Long.MIN_VALUE;
    }
    public List<Device> devices(long now) {
        if (now >= nextRefresh) {
            var result = new ArrayList<Device>(nodes.size());
            var owners = new IdentityHashMap<Object, Boolean>(nodes.size());
            var iterator = nodes.reference2ObjectEntrySet().fastIterator();
            while (iterator.hasNext()) {
                // fastIterator reuses its entry; only the node and ID may escape.
                var entry = iterator.next();
                var node = entry.getKey();
                if (node.getOwner() instanceof appeng.api.networking.security.IActionHost host
                        && host.getActionableNode() != node && nodes.containsKey(host.getActionableNode())) continue;
                if (owners.put(node.getOwner(), true) != null) continue;
                var location = DeviceLocation.of(node);
                if (location != null) result.add(new Device(entry.getValue(), node, location, node.isActive()));
            }
            snapshot = List.copyOf(result);
            nextRefresh = now + 20;
        }
        return snapshot;
    }
    public boolean contains(IGridNode node) { return nodes.containsKey(node); }
    public IGridNode resolve(UUID id) {
        var iterator = nodes.reference2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().equals(id)) return entry.getKey();
        }
        return null;
    }
}
