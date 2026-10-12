package com.moakiee.ae2lt.logic.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;

class PowerCostServiceAccessTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void cachedServiceStillChecksLivePowerAndIdleDrain() {
        var energy = new Energy();
        var access = new PowerCostUtil.EnergyAccess();
        var key = AEItemKey.of(Items.STONE);
        for (int transfer = 0; transfer < 6; transfer++) {
            assertEquals(64, access.maxAffordable(energy.grid, key, 64));
            access.consume(energy.grid, key, 64);
        }
        assertEquals(4, energy.stored);
        assertEquals(0, access.maxAffordable(energy.grid, key, 64));
        assertEquals(1, energy.lookups.get());
        assertEquals(7, energy.simulations);
        assertEquals(6, energy.charges);

        energy.stored = 20;
        energy.idle = 12;
        assertEquals(32, access.maxAffordable(energy.grid, key, 64));
    }

    @Test
    void gridChangeAndOfflineTransitionDiscardCachedService() {
        var first = new Energy();
        var second = new Energy();
        second.stored = 4;
        var key = AEItemKey.of(Items.STONE);
        var access = new PowerCostUtil.EnergyAccess();

        assertEquals(64, access.maxAffordable(first.grid, key, 64));
        assertEquals(0, access.maxAffordable(second.grid, key, 64));
        assertEquals(0, access.maxAffordable(null, key, 64));
        access.consume(null, key, 64);
        assertEquals(100, first.stored);
        assertEquals(4, second.stored);
        assertEquals(64, access.maxAffordable(first.grid, key, 64));
        access.consume(first.grid, key, 64);
        assertEquals(84, first.stored);
        assertEquals(2, first.lookups.get());
        assertEquals(1, second.lookups.get());
    }

    @Test
    void batchPrepaymentSurvivesExternalDrainAndRefundsUnusedEnergy() {
        var energy = new Energy();
        var access = new PowerCostUtil.EnergyAccess();
        var key = AEItemKey.of(Items.STONE);
        access.beginBatch(energy.grid);
        assertEquals(64, access.maxAffordable(energy.grid, key, 64));
        assertEquals(4, energy.stored);
        energy.stored = 0; // An inventory callback draws from the same grid.
        access.consume(energy.grid, key, 64);
        assertEquals(64, access.maxAffordable(energy.grid, key, 64));
        access.consume(energy.grid, key, 32);
        access.endBatch();
        assertEquals(72, energy.stored);
        assertEquals(1, energy.simulations);
        assertEquals(1, energy.charges);
    }

    @Test
    void batchReservesIdlePowerAndChargesPartialTransfersOnly() {
        var energy = new Energy();
        energy.stored = 20;
        energy.idle = 12;
        var access = new PowerCostUtil.EnergyAccess();
        var key = AEItemKey.of(Items.STONE);
        access.beginBatch(energy.grid);
        assertEquals(32, access.maxAffordable(energy.grid, key, 64));
        access.consume(energy.grid, key, 5);
        access.endBatch();
        assertEquals(18, energy.stored);
        access.endBatch();
        assertEquals(18, energy.stored);
    }

    @Test
    void batchRefundUsesOriginalServiceAfterCallbackChangesGrid() {
        var first = new Energy();
        var second = new Energy();
        var access = new PowerCostUtil.EnergyAccess();
        var key = AEItemKey.of(Items.STONE);
        access.beginBatch(first.grid);
        assertEquals(64, access.maxAffordable(first.grid, key, 64));
        access.consume(second.grid, key, 64);
        assertEquals(64, access.maxAffordable(second.grid, key, 64));
        assertEquals(84, first.stored);
        access.consume(second.grid, key, 64);
        access.endBatch();
        assertEquals(84, second.stored);
    }

    @Test
    void unusedPrepaymentRefundsPhysicalAeWithConfiguredMultiplier() {
        double multiplier = appeng.api.config.PowerMultiplier.CONFIG.multiplier;
        try {
            appeng.api.config.PowerMultiplier.CONFIG.multiplier = 2;
            var energy = new Energy();
            var access = new PowerCostUtil.EnergyAccess();
            var key = AEItemKey.of(Items.STONE);
            access.beginBatch(energy.grid);
            assertEquals(64, access.maxAffordable(energy.grid, key, 64));
            access.consume(energy.grid, key, 64);
            access.endBatch();
            assertEquals(68, energy.stored);
        } finally { appeng.api.config.PowerMultiplier.CONFIG.multiplier = multiplier; }
    }

    @Test
    void rejectedRefundRemainsAvailableForTheNextBatch() {
        var energy = new Energy();
        energy.capacity = 100;
        var access = new PowerCostUtil.EnergyAccess();
        var key = AEItemKey.of(Items.STONE);
        access.beginBatch(energy.grid);
        assertEquals(64, access.maxAffordable(energy.grid, key, 64));
        energy.stored = 100;
        access.consume(energy.grid, key, 64);
        assertTrue(access.endBatch());
        assertEquals(80, access.storedEnergy());

        access.beginBatch(energy.grid);
        assertEquals(64, access.maxAffordable(energy.grid, key, 64));
        access.consume(energy.grid, key, 64);
        assertTrue(access.endBatch());
        assertEquals(64, access.storedEnergy());
        assertEquals(100, energy.stored);
        assertEquals(1, energy.charges);
        assertFalse(access.endBatch());
    }

    @Test
    void partialRefundKeepsOnlyTheRejectedPhysicalEnergy() {
        var energy = new Energy();
        energy.capacity = 100;
        var access = new PowerCostUtil.EnergyAccess();
        var key = AEItemKey.of(Items.STONE);
        access.beginBatch(energy.grid);
        access.maxAffordable(energy.grid, key, 64);
        access.consume(energy.grid, key, 64);
        energy.stored = 70;
        access.endBatch();
        assertEquals(100, energy.stored);
        assertEquals(50, access.storedEnergy());

        energy.stored = 0;
        access.beginBatch(energy.grid);
        assertEquals(50, energy.stored);
        assertEquals(64, access.maxAffordable(energy.grid, key, 64));
        access.consume(energy.grid, key, 64);
        assertTrue(access.endBatch());
        assertEquals(34, energy.stored);
        assertEquals(0, access.storedEnergy());
    }

    @Test
    void restoredCreditUsesPhysicalAeAcrossMultiplierChanges() {
        double multiplier = appeng.api.config.PowerMultiplier.CONFIG.multiplier;
        try {
            var energy = new Energy();
            energy.capacity = 100;
            var access = new PowerCostUtil.EnergyAccess();
            access.loadStoredEnergy(40);
            appeng.api.config.PowerMultiplier.CONFIG.multiplier = 2;
            var key = AEItemKey.of(Items.STONE);
            access.beginBatch(energy.grid);
            assertEquals(64, access.maxAffordable(energy.grid, key, 64));
            appeng.api.config.PowerMultiplier.CONFIG.multiplier = 3;
            access.consume(energy.grid, key, 64);
            assertTrue(access.endBatch());
            assertEquals(8, access.storedEnergy());
            assertEquals(100, energy.stored);
            assertEquals(0, energy.charges);

            var restored = new PowerCostUtil.EnergyAccess();
            restored.loadStoredEnergy(access.storedEnergy());
            restored.beginBatch(energy.grid);
            assertEquals(8, restored.maxAffordable(energy.grid, key, 8));
            restored.consume(energy.grid, key, 8);
            restored.endBatch();
            assertEquals(2, restored.storedEnergy());
        } finally { appeng.api.config.PowerMultiplier.CONFIG.multiplier = multiplier; }
    }

    @Test
    void corruptPersistedEnergyCannotCreateCredit() {
        var access = new PowerCostUtil.EnergyAccess();
        for (double invalid : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            access.loadStoredEnergy(invalid);
            assertEquals(0, access.storedEnergy());
        }
    }

    @Test
    void failedLookupDoesNotCacheTheGrid() {
        var energy = new Energy();
        var attempts = new AtomicInteger();
        var unreliable = (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(),
                new Class<?>[] {IGrid.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getService") || method.getName().equals("getEnergyService")) {
                        if (attempts.getAndIncrement() == 0) {
                            throw new IllegalStateException("lookup interrupted");
                        }
                        return energy.service;
                    }
                    throw new AssertionError(method);
                });
        var access = new PowerCostUtil.EnergyAccess();
        var key = AEItemKey.of(Items.STONE);

        assertThrows(IllegalStateException.class, () -> access.maxAffordable(unreliable, key, 64));
        assertEquals(64, access.maxAffordable(unreliable, key, 64));
        assertEquals(2, attempts.get());
    }

    private static final class Energy {
        double stored = 100;
        double idle = 4;
        double capacity = Double.POSITIVE_INFINITY;
        int simulations;
        int charges;
        final AtomicInteger lookups = new AtomicInteger();
        final IEnergyService service = (IEnergyService) Proxy.newProxyInstance(
                IEnergyService.class.getClassLoader(), new Class<?>[] {IEnergyService.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getIdlePowerUsage")) return idle;
                    if (method.getName().equals("injectPower")) {
                        double offered = (Double) args[0];
                        double accepted = Math.min(offered, capacity - stored);
                        if (args[1] == Actionable.MODULATE) stored += accepted;
                        return offered - accepted;
                    }
                    if (method.getName().equals("extractAEPower")) {
                        var multiplier = (appeng.api.config.PowerMultiplier) args[2];
                        double received = Math.min(stored, multiplier.multiply((Double) args[0]));
                        if (args[1] == Actionable.MODULATE) {
                            stored -= received;
                            charges++;
                        } else {
                            simulations++;
                        }
                        return multiplier.divide(received);
                    }
                    throw new AssertionError(method);
                });
        final IGrid grid = (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(),
                new Class<?>[] {IGrid.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getService") || method.getName().equals("getEnergyService")) {
                        lookups.incrementAndGet();
                        return service;
                    }
                    throw new AssertionError(method);
                });
    }
}
