package com.moakiee.ae2lt.logic.tianshu.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.logic.tianshu.terminal.SeedRefillSync;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TianshuSeedRefillServiceTest {
    private static final IActionSource SOURCE = IActionSource.empty();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void obsoleteSeedsMakeRoomForNewRequirements() {
        var oldSeed = AEItemKey.of(Items.IRON_INGOT);
        var newSeed = AEItemKey.of(Items.GOLD_INGOT);
        var seeds = new Storage(8).with(oldSeed, 8);
        var network = new Storage(100).with(newSeed, 8);

        var result = TianshuSeedRefillService.reconcile(Map.of(newSeed, 8L), seeds, network, SOURCE);

        assertTrue(result.complete());
        assertEquals(8L, result.returned().get(oldSeed));
        assertEquals(8L, result.moved().get(newSeed));
        assertEquals(0, seeds.amount(oldSeed));
        assertEquals(8, seeds.amount(newSeed));
        assertEquals(8, network.amount(oldSeed));
    }

    @Test
    void rejectedReturnStaysOwnedAndReportsFailure() {
        var seed = AEItemKey.of(Items.IRON_INGOT);
        var seeds = new Storage(100).with(seed, 8);
        var network = new Storage(0);

        var result = TianshuSeedRefillService.reconcile(Map.of(), seeds, network, SOURCE);

        assertFalse(result.complete());
        assertEquals(8, seeds.amount(seed));
        assertEquals(0, seeds.extractionCalls);
        assertEquals(8L, result.returnBlocked().get(seed));
        var sync = SeedRefillSync.of(result);
        assertEquals(SeedRefillSync.STATE_RETURN_BLOCKED, sync.state());
        assertEquals(8L, sync.problems().get(0).returnBlocked());
    }

    @Test
    void actualInsertRejectingAfterSimulationRefundsRemainder() {
        var seed = AEItemKey.of(Items.IRON_INGOT);
        var seeds = new Storage(100).with(seed, 8);
        var network = new Storage(100);
        network.actualInsertLimit = 3;

        var result = TianshuSeedRefillService.reconcile(Map.of(), seeds, network, SOURCE);

        assertEquals(3L, result.returned().get(seed));
        assertEquals(5L, result.returnBlocked().get(seed));
        assertEquals(5, seeds.amount(seed));
        assertEquals(3, network.amount(seed));
    }

    private static final class Storage implements MEStorage {
        private final Map<AEKey, Long> amounts = new HashMap<>();
        private final long capacity;
        private long actualInsertLimit = Long.MAX_VALUE;
        private int extractionCalls;

        private Storage(long capacity) {
            this.capacity = capacity;
        }

        private Storage with(AEKey key, long amount) {
            amounts.put(key, amount);
            return this;
        }

        private long amount(AEKey key) {
            return amounts.getOrDefault(key, 0L);
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            long used = amounts.values().stream().mapToLong(Long::longValue).sum();
            long accepted = Math.min(amount, Math.max(0, capacity - used));
            if (mode == Actionable.MODULATE) {
                accepted = Math.min(accepted, actualInsertLimit);
                amounts.merge(key, accepted, Long::sum);
            }
            return accepted;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long extracted = Math.min(amount, amount(key));
            if (mode == Actionable.MODULATE) {
                extractionCalls++;
                amounts.put(key, amount(key) - extracted);
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            amounts.forEach(out::add);
        }

        @Override
        public Component getDescription() {
            return Component.literal("seed test storage");
        }
    }
}
