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
    private static final String TICK_BUDGET = "ae2ltEnergyTickBudget";
    private final Map<IGridNode, UUID> claims = new IdentityHashMap<>();
    private EnergyTickBudget budget;
    private appeng.api.networking.IGrid grid;
    private long topologyTick = Long.MIN_VALUE;
    private long topologyRevision;
    private boolean supplementing;

    @Override public void addNode(IGridNode node, CompoundTag data) {
        topologyTick = node.getLevel().getServer().getTickCount();
        topologyRevision++;
        grid = node.getGrid();
        if (data != null && data.hasUUID(TICK_BUDGET)) {
            var inherited = WirelessEnergyLedger.get(node.getLevel().getServer())
                    .transferredBudget(topologyTick, data.getUUID(TICK_BUDGET));
            if (inherited != null) budget(topologyTick).merge(inherited);
        }
        if (data != null && data.hasUUID(CLAIM)) claims.put(node, data.getUUID(CLAIM));
    }
    @Override public void saveNodeData(IGridNode node, CompoundTag data) {
        UUID id = claims.get(node);
        if (id != null) data.putUUID(CLAIM, id);
        // AE2 calls saveNodeData before removing a migrating node from its old grid.
        var current = budget(node.getLevel().getServer().getTickCount());
        WirelessEnergyLedger.get(node.getLevel().getServer()).registerBudget(current);
        data.putUUID(TICK_BUDGET, current.id);
    }
    @Override public void removeNode(IGridNode node) {
        topologyTick = node.getLevel().getServer().getTickCount();
        topologyRevision++;
        claims.remove(node);
    }

    public EnergyEscrow escrow(IGridNode node) {
        return WirelessEnergyLedger.get(node.getLevel().getServer()).claim(claimId(node), node);
    }
    private UUID claimId(IGridNode node) {
        return claims.computeIfAbsent(node, ignored -> {
            if (node.getOwner() instanceof BlockEntity be) be.setChanged();
            else if (node.getOwner() instanceof appeng.parts.AEBasePart part) part.getBlockEntity().setChanged();
            return UUID.randomUUID();
        });
    }
    public AeEnergyBalance aeBalance(IGridNode node) {
        return WirelessEnergyLedger.get(node.getLevel().getServer()).aeAccount(claimId(node));
    }
    EnergyEscrow peekEscrow(IGridNode node) {
        return WirelessEnergyLedger.get(node.getLevel().getServer()).peekAccount(claims.get(node));
    }
    AeEnergyBalance peekAeBalance(IGridNode node) {
        return WirelessEnergyLedger.get(node.getLevel().getServer()).peekAeAccount(claims.get(node));
    }
    boolean beginSupplement() { if (supplementing) return false; supplementing = true; return true; }
    void endSupplement() { supplementing = false; }
    long topologyRevision() { return topologyRevision; }
    private EnergyTickBudget budget(long tick) {
        if (budget == null || budget.tick != tick) budget = new EnergyTickBudget(tick);
        return budget;
    }
    /** Conservative maintenance allowance, shared with all output and request attempts. */
    long maintenanceReserveFe() {
        if (grid == null || !WirelessEnergySettings.runningNetworkFeFallback.get()
                || !grid.getEnergyService().isNetworkPowered()
                || !grid.getActiveMachines(com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity.class)
                        .iterator().hasNext()) return 0;
        double rate = appeng.api.config.PowerUnit.FE.convertTo(appeng.api.config.PowerUnit.AE, 1);
        double raw = appeng.api.config.PowerMultiplier.CONFIG.multiply(Math.max(.1, grid.getEnergyService().getIdlePowerUsage()));
        if (!(rate > 0) || !Double.isFinite(rate) || !(raw > 0) || !Double.isFinite(raw)) return Long.MAX_VALUE;
        return new AeEnergyBalance("0", () -> {}).neededFe(raw, rate);
    }
    long preview(long tick, long request, long feLimit, int targetLimit) {
        if (topologyPaused(tick)) return 0;
        return previewAe(tick, request, feLimit, targetLimit, false);
    }
    long previewAe(long tick, long request, long feLimit, int targetLimit, boolean maintenance) {
        long reserved = maintenance ? 0 : maintenanceReserveFe();
        var current = budget != null && budget.tick == tick ? budget : new EnergyTickBudget(tick);
        return current.preview(request, feLimit, targetLimit, reserved, reserved > 0 ? 1 : 0);
    }
    long reserveAe(long tick, long request, long feLimit, int targetLimit, boolean maintenance) {
        long reserved = maintenance ? 0 : maintenanceReserveFe();
        return budget(tick).reserve(request, feLimit, targetLimit, reserved, reserved > 0 ? 1 : 0);
    }
    /** Shared, charged before callbacks. Multiple controllers cannot each get a full allowance. */
    boolean topologyPaused(long tick) { return tick == topologyTick; }
    public long reserve(long tick, long request, long feLimit, int targetLimit) {
        // A topology change pauses output for the remainder of this server tick.
        // This prevents split/merge from resetting a spent budget into several fresh allowances.
        if (tick == topologyTick) return 0;
        return reserveAe(tick, request, feLimit, targetLimit, false);
    }
}
