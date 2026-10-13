package com.moakiee.ae2lt.logic.energy;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Node-owned claims move on split/merge; balances are never copied into either grid. */
public final class WirelessEnergyGridService implements IGridService, IGridServiceProvider {
    private static final String CLAIM = "ae2ltEnergyClaim";
    private final Map<IGridNode, UUID> claims = new IdentityHashMap<>();
    private long budgetTick = Long.MIN_VALUE;
    private long spent;
    private long topologyTick = Long.MIN_VALUE;
    private int visited;

    @Override public void addNode(IGridNode node, CompoundTag data) {
        topologyTick = node.getLevel().getServer().getTickCount();
        if (data != null && data.hasUUID(CLAIM)) claims.put(node, data.getUUID(CLAIM));
    }
    @Override public void saveNodeData(IGridNode node, CompoundTag data) {
        UUID id = claims.get(node);
        if (id != null) data.putUUID(CLAIM, id);
    }
    @Override public void removeNode(IGridNode node) {
        topologyTick = node.getLevel().getServer().getTickCount();
        claims.remove(node);
    }

    public EnergyEscrow escrow(IGridNode node) {
        UUID id = claims.computeIfAbsent(node, ignored -> {
            if (node.getOwner() instanceof BlockEntity be) be.setChanged();
            else if (node.getOwner() instanceof appeng.parts.AEBasePart part) part.getBlockEntity().setChanged();
            return UUID.randomUUID();
        });
        return WirelessEnergyLedger.get(node.getLevel().getServer()).claim(id, node);
    }
    /** Shared, charged before callbacks. Multiple controllers cannot each get a full allowance. */
    boolean topologyPaused(long tick) { return tick == topologyTick; }
    public long reserve(long tick, long request, long feLimit, int targetLimit) {
        // A topology change pauses output for the remainder of this server tick.
        // This prevents split/merge from resetting a spent budget into several fresh allowances.
        if (tick == topologyTick) return 0;
        if (budgetTick != tick) { budgetTick = tick; spent = 0; visited = 0; }
        if (visited >= targetLimit) return 0;
        visited++;
        long allowed = Math.min(Math.max(0, request), Math.max(0, feLimit - spent));
        spent += allowed;
        return allowed;
    }
}
