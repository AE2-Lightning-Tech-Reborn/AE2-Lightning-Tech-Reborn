package com.moakiee.ae2lt.machine.largeoverload;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import net.minecraft.nbt.CompoundTag;

/** Durable external-node anchors, used only to route this factory's retained resources. */
public final class LargeFactoryNetworkIdentity implements IGridService, IGridServiceProvider {
    private static final String TAG = "ae2ltLargeFactoryAnchor";
    private final Map<IGridNode, UUID> nodes = new IdentityHashMap<>();
    private final Map<UUID, Integer> anchors = new HashMap<>();

    @Override public void addNode(IGridNode node, CompoundTag savedData) {
        if (nodes.containsKey(node)) return;
        UUID id = savedData != null && savedData.hasUUID(TAG) ? savedData.getUUID(TAG) : UUID.randomUUID();
        nodes.put(node, id);
        anchors.merge(id, 1, Integer::sum);
    }
    @Override public void saveNodeData(IGridNode node, CompoundTag savedData) {
        UUID id = nodes.get(node);
        if (id != null) savedData.putUUID(TAG, id);
    }
    @Override public void removeNode(IGridNode node) {
        UUID id = nodes.remove(node);
        if (id != null) anchors.computeIfPresent(id, (ignored, count) -> count <= 1 ? null : count - 1);
    }
    public boolean contains(UUID id) { return id != null && anchors.containsKey(id); }
    public UUID externalAnchor(IGridNode hatch) {
        for (var connection : hatch.getConnections()) {
            var other = connection.getOtherSide(hatch);
            if (!(other.getOwner() instanceof LargeFactoryHatchBlockEntity)) {
                UUID id = nodes.get(other);
                if (id != null) return id;
            }
        }
        return null;
    }
}
