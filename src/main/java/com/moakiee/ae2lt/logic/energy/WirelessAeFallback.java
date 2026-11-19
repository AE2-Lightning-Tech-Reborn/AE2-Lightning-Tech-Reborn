package com.moakiee.ae2lt.logic.energy;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;

/** Same-call shortfall settlement. Never advertises FE as AE battery capacity. */
public final class WirelessAeFallback {
    private static final ThreadLocal<Boolean> SUPPLEMENTING = ThreadLocal.withInitial(() -> false);
    private WirelessAeFallback() {}

    public static double supplement(IGrid grid, boolean alreadyRunning, double requested, double original,
                                    Actionable mode, PowerMultiplier multiplier, boolean maintenance) {
        if (SUPPLEMENTING.get() || !alreadyRunning || !WirelessEnergySettings.runningNetworkFeFallback.get()
                || !AppFluxBridge.canUseEnergyHandler() || AppFluxBridge.FE_KEY == null
                || !Double.isFinite(requested) || !Double.isFinite(original) || original < 0 || requested <= original) return original;
        double rawMissing = multiplier.multiply(requested - original);
        double rate = PowerUnit.FE.convertTo(PowerUnit.AE, 1);
        if (!(rawMissing > 0) || !Double.isFinite(rawMissing) || !(rate > 0) || !Double.isFinite(rate)) return original;
        var service = grid.getService(WirelessEnergyGridService.class);
        if (!service.beginSupplement()) return original;
        SUPPLEMENTING.set(true);
        try {
            AdvancedWirelessOverloadedControllerBlockEntity controller = null;
            for (var candidate : grid.getActiveMachines(AdvancedWirelessOverloadedControllerBlockEntity.class)) {
                if (!candidate.isRemoved() && candidate.getMainNode().getGrid() == grid) { controller = candidate; break; }
            }
            if (controller == null) return original;
            final var c = controller;
            var node = c.getMainNode().getNode();
            if (node == null || !node.getLevel().getServer().isSameThread()) return original;
            long tick = node.getLevel().getServer().getTickCount();
            long revision = service.topologyRevision();
            java.util.function.BooleanSupplier valid = () -> !c.isRemoved() && c.getMainNode().getNode() == node
                    && node.getGrid() == grid && node.isActive() && grid.getEnergyService().isNetworkPowered()
                    && service.topologyRevision() == revision && PowerUnit.FE.convertTo(PowerUnit.AE, 1) == rate;
            if (!valid.getAsBoolean()) return original;
            var fe = mode == Actionable.SIMULATE ? service.peekEscrow(node) : service.escrow(node);
            var ae = mode == Actionable.SIMULATE ? service.peekAeBalance(node) : service.aeBalance(node);
            // A failed/unfinished storage extraction is not a receipt and cannot be replayed.
            if (ae.pendingFeWithdrawal() != 0) return original;
            long needed = ae.neededFe(rawMissing, rate);
            long grant = mode == Actionable.SIMULATE
                    ? service.previewAe(tick, needed, WirelessEnergySettings.networkFe.get(), WirelessEnergySettings.networkTargets.get(), maintenance)
                    : needed == 0 ? 0 : service.reserveAe(tick, needed, WirelessEnergySettings.networkFe.get(), WirelessEnergySettings.networkTargets.get(), maintenance);
            var inventory = new GuardedEnergyStorage(grid.getStorageService().getInventory(), valid);
            var source = IActionSource.ofMachine(c);
            long owned = Math.min(grant, fe.available());
            long withdrawal = Math.min(grant - owned, fe.room());
            if (mode == Actionable.SIMULATE) {
                long simulated = withdrawal == 0 ? 0 : Math.max(0, Math.min(withdrawal,
                        inventory.extract(AppFluxBridge.FE_KEY, withdrawal, Actionable.SIMULATE, source)));
                if (!valid.getAsBoolean()) return original;
                return combined(requested, original, ae.simulate(rawMissing, owned + simulated, rate), multiplier);
            }
            if (withdrawal > 0) {
                ae.beginWithdrawal(withdrawal);
                long received = inventory.extract(AppFluxBridge.FE_KEY, withdrawal, Actionable.MODULATE, source);
                ae.completeWithdrawal(fe, received);
            }
            if (!valid.getAsBoolean()) return original; // already withdrawn FE stays with this node claim
            ae.convert(fe, Math.min(grant, fe.available()), rate);
            // All external callbacks finish before committing an AE payment. Failed refunds
            // remain FE escrow/uncertain; a callback exception cannot erase paid-but-unreturned AE.
            // A payment from already converted AE needs no storage IO. Retry refunds
            // only when this call obtained an IO visit, never through a zero-grant path.
            if (grant > 0 && fe.available() > 0) new BufferedMEStorage(inventory, null, null, fe).endBatch(AppFluxBridge.FE_KEY, source);
            if (!valid.getAsBoolean()) return original;
            double result = combined(requested, original, Math.min(rawMissing, ae.available()), multiplier);
            double cost = multiplier.multiply(result - original);
            if (cost > 0) ae.extract(cost);
            return result;
        } catch (RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("Running-network FE supplement interrupted; retained balances are not replayed", e);
            return original;
        } finally { service.endSupplement(); SUPPLEMENTING.remove(); }
    }

    /** Round the returned total conservatively, then charge exactly its represented difference. */
    private static double combined(double request, double original, double rawAvailable, PowerMultiplier multiplier) {
        double result = Math.min(request, original + multiplier.divide(rawAvailable));
        if (multiplier.multiply(result - original) > rawAvailable) result = Math.nextDown(result);
        return Math.max(original, result);
    }
}
