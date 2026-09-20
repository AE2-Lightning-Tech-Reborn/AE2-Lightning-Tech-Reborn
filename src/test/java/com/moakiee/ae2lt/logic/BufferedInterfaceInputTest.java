package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.helpers.externalstorage.GenericStackItemStorage;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.loading.LoadingModList;

class BufferedInterfaceInputTest {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static AEItemKey variant(int i) {
        var stack = new ItemStack(Items.STONE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("buffer-" + i));
        return AEItemKey.of(stack);
    }

    @Test
    void repeatedRealItemHandlerSimulationsAndInsertionsCoalesceBeforeNetworkAccess() {
        var queue = new BufferedInterfaceInput();
        var network = new Storage();
        var delegate = new GenericStackInv(Set.of(AEKeyType.items()), null, GenericStackInv.Mode.STORAGE, 36) {
            @Override public long insert(int slot, AEKey key, long amount, Actionable mode) {
                return network.insert(key, amount, mode, IActionSource.empty());
            }
        };
        var direct = new GenericStackItemStorage(delegate);
        var buffered = new GenericStackItemStorage(new FilteredInsertGenericInv(
                delegate, key -> !key.equals(AEItemKey.of(Items.DIRT)),
                (slot, key, amount, mode) -> queue.insert(key, amount, mode)));
        for (int i = 0; i < 10_000; i++) {
            assertTrue(direct.insertItem(0, new ItemStack(Items.STONE), true).isEmpty());
            assertTrue(direct.insertItem(0, new ItemStack(Items.STONE), false).isEmpty());
        }
        assertEquals(20_000, network.calls);
        network.calls = 0;
        network.accepted.clear();
        for (int i = 0; i < 10_000; i++) {
            assertTrue(buffered.insertItem(0, new ItemStack(Items.STONE), true).isEmpty());
            assertTrue(buffered.insertItem(0, new ItemStack(Items.STONE), false).isEmpty());
        }
        assertEquals(1, buffered.insertItem(0, new ItemStack(Items.DIRT), true).getCount());
        assertEquals(0, network.calls);
        assertEquals(10_000, queue.amount(AEItemKey.of(Items.STONE)));
        queue.flush(network, IActionSource.empty(), 5, 0, () -> {});
        assertEquals(1, network.calls);
        assertEquals(10_000, network.accepted.get(AEItemKey.of(Items.STONE)));
        assertTrue(queue.isEmpty());
        System.out.println("passive-input comparison: 10000 simulate+insert pairs; network calls 20000 -> 1; conserved=10000");
    }

    @Test
    void capacityIsSharedAcrossKeysAndSimulationNeverReservesIt() {
        var queue = new BufferedInterfaceInput();
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        long capacity = BufferedInterfaceInput.capacity(stone.getType());
        assertEquals(capacity, queue.insert(stone, Long.MAX_VALUE, Actionable.SIMULATE));
        assertEquals(capacity, queue.insert(stone, Long.MAX_VALUE, Actionable.SIMULATE));
        assertTrue(queue.isEmpty());
        assertEquals(capacity - 7, queue.insert(stone, capacity - 7, Actionable.MODULATE));
        assertEquals(7, queue.insert(dirt, Long.MAX_VALUE, Actionable.MODULATE));
        assertEquals(0, queue.insert(stone, 1, Actionable.MODULATE));
        var water = AEFluidKey.of(Fluids.WATER);
        assertEquals(1000, queue.insert(water, 1000, Actionable.MODULATE));
        assertEquals(0, queue.insert(stone, -1, Actionable.MODULATE));
        assertEquals(capacity - 7, queue.amount(stone));
    }

    @Test
    void distinctKeyLimitStillAllowsMergingExistingKeys() {
        var queue = new BufferedInterfaceInput();
        for (int i = 0; i < BufferedInterfaceInput.MAX_KEYS; i++) {
            assertEquals(1, queue.insert(variant(i), 1, Actionable.MODULATE));
        }
        assertEquals(0, queue.insert(variant(BufferedInterfaceInput.MAX_KEYS), 1, Actionable.SIMULATE));
        assertEquals(1, queue.insert(variant(0), 1, Actionable.MODULATE));
        assertEquals(2, queue.amount(variant(0)));
    }

    @Test
    void everyFlushIsBoundedAndFiveTicksApartEvenWithContinuousInput() {
        var queue = new BufferedInterfaceInput();
        var network = new Storage();
        for (int i = 0; i < 400; i++) queue.insert(variant(i), 2, Actionable.MODULATE);
        for (int tick = 0; tick <= 20; tick++) {
            int before = network.calls;
            queue.flush(network, IActionSource.empty(), tick, 2, () -> {});
            int calls = network.calls - before;
            assertTrue(calls <= BufferedInterfaceInput.FLUSH_MAX_KEYS);
            if (calls > 0) assertEquals(2, tick % 5);
            queue.flush(network, IActionSource.empty(), tick, 2, () -> fail("duplicate flush"));
            assertEquals(before + calls, network.calls);
        }
        assertTrue(queue.isEmpty());
        assertEquals(800, network.accepted.values().stream().mapToLong(Long::longValue).sum());
        // New arrivals must not force an extra flush in the same five-tick window.
        queue.insert(variant(0), 1, Actionable.MODULATE);
        queue.flush(network, IActionSource.empty(), 17, 2, () -> fail("early flush"));
        assertEquals(1, queue.amount(variant(0)));
        queue.flush(network, IActionSource.empty(), 22, 2, () -> {});
        assertTrue(queue.isEmpty());
    }

    @Test
    void rejectedPrefixDoesNotStarveTailAndPartialInsertionsConserveOwnership() {
        var queue = new BufferedInterfaceInput();
        var network = new Storage();
        network.limit = 0;
        for (int i = 0; i < 129; i++) queue.insert(variant(i), 10, Actionable.MODULATE);
        queue.flush(network, IActionSource.empty(), 0, 0, () -> fail("rejection changes no amounts"));
        network.limit = 3;
        network.keys.clear();
        queue.flush(network, IActionSource.empty(), 5, 0, () -> {});
        assertEquals(variant(128), network.keys.getFirst());
        for (int i = 0; i < 129; i++) {
            assertEquals(10, queue.amount(variant(i)) + network.accepted.getOrDefault(variant(i), 0L));
        }
        network.limit = Long.MAX_VALUE;
        for (int tick = 10; tick < 30; tick += 5) queue.flush(network, IActionSource.empty(), tick, 0, () -> {});
        assertTrue(queue.isEmpty());
        assertEquals(1290, network.accepted.values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void storageCannotReinsertIntoTheQueueBeingFlushed() {
        var queue = new BufferedInterfaceInput();
        var key = AEItemKey.of(Items.STONE);
        queue.insert(key, 64, Actionable.MODULATE);
        var loop = new Storage() {
            @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
                assertEquals(0, queue.insert(what, amount, Actionable.SIMULATE));
                assertEquals(0, queue.insert(what, amount, Actionable.MODULATE));
                return 0;
            }
        };
        queue.flush(loop, IActionSource.empty(), 0, 0, () -> fail("reentrant rejection must retain ownership"));
        assertEquals(64, queue.amount(key));
        assertEquals(1, queue.insert(key, 1, Actionable.MODULATE));
    }

    @Test
    void completedTransfersAreMarkedDirtyEvenIfALaterStorageCallThrows() {
        var queue = new BufferedInterfaceInput();
        queue.insert(variant(0), 5, Actionable.MODULATE);
        queue.insert(variant(1), 7, Actionable.MODULATE);
        var saves = new AtomicInteger();
        var storage = new Storage() {
            @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                if (key.equals(variant(1))) throw new IllegalStateException("test failure");
                return super.insert(key, amount, mode, source);
            }
        };
        assertThrows(IllegalStateException.class, () -> queue.flush(storage, IActionSource.empty(), 0, 0, saves::incrementAndGet));
        assertEquals(1, saves.get());
        assertEquals(0, queue.amount(variant(0)));
        assertEquals(7, queue.amount(variant(1)));
        assertEquals(1, queue.insert(variant(1), 1, Actionable.MODULATE));
    }

    private static class Storage implements MEStorage {
        int calls;
        long limit = Long.MAX_VALUE;
        final List<AEKey> keys = new ArrayList<>();
        final Map<AEKey, Long> accepted = new HashMap<>();
        @Override public Component getDescription() { return Component.literal("test storage"); }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            calls++;
            keys.add(key);
            long moved = Math.min(limit, amount);
            if (mode == Actionable.MODULATE) accepted.merge(key, moved, Long::sum);
            return moved;
        }
    }
}
