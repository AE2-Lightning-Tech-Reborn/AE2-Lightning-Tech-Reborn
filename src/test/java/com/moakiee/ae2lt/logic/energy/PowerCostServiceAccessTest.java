package com.moakiee.ae2lt.logic.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        int simulations;
        int charges;
        final AtomicInteger lookups = new AtomicInteger();
        final IEnergyService service = (IEnergyService) Proxy.newProxyInstance(
                IEnergyService.class.getClassLoader(), new Class<?>[] {IEnergyService.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getIdlePowerUsage")) return idle;
                    if (method.getName().equals("extractAEPower")) {
                        double received = Math.min(stored, (Double) args[0]);
                        if (args[1] == Actionable.MODULATE) {
                            stored -= received;
                            charges++;
                        } else {
                            simulations++;
                        }
                        return received;
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
