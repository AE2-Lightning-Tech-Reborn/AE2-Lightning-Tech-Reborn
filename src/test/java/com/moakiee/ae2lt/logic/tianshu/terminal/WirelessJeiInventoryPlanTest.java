package com.moakiee.ae2lt.logic.tianshu.terminal;

import static org.junit.jupiter.api.Assertions.*;

import com.moakiee.ae2lt.network.jei.WirelessJeiSupplyPacket;
import com.moakiee.ae2lt.network.jei.WirelessJeiSupplyResultPacket;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WirelessJeiInventoryPlanTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<ItemStack> emptyInventory() {
        return new ArrayList<>(Collections.nCopies(36, ItemStack.EMPTY));
    }

    @Test
    void simulationMergesIdenticalStacksWithoutMutatingInput() {
        var inventory = emptyInventory();
        var stack = new ItemStack(Items.DIAMOND, 60);
        inventory.set(0, stack);
        var result = WirelessJeiInventoryPlan.insert(inventory, List.of(new ItemStack(Items.DIAMOND, 70)));
        assertNotNull(result);
        assertEquals(64, result.get(0).getCount());
        assertEquals(64, result.get(1).getCount());
        assertEquals(2, result.get(2).getCount());
        assertEquals(60, inventory.get(0).getCount());
        assertTrue(inventory.get(1).isEmpty());
        assertEquals(60, stack.getCount());
    }

    @Test
    void refusesOverflowAndMalformedRequests() {
        var full = new ArrayList<ItemStack>();
        for (int slot = 0; slot < 36; slot++) full.add(new ItemStack(Items.STONE, 64));
        assertNull(WirelessJeiInventoryPlan.insert(full, List.of(new ItemStack(Items.DIAMOND))));
        assertNull(WirelessJeiInventoryPlan.insert(emptyInventory(), List.of(ItemStack.EMPTY)));
        assertNull(WirelessJeiInventoryPlan.insert(List.of(), List.of(new ItemStack(Items.DIAMOND))));
        assertEquals(36, full.size());
    }

    @Test
    void supplyPacketsRoundTripWithoutMutatingStacks() {
        var item = new ItemStack(Items.DIAMOND, 7);
        item.getOrCreateTag().putString("Origin", "ME");
        var query = new WirelessJeiSupplyPacket(13, 22, false, List.of(item));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            query.write(buffer);
            var decoded = WirelessJeiSupplyPacket.read(buffer);
            assertEquals(13, decoded.containerId());
            assertEquals(22, decoded.requestId());
            assertFalse(decoded.take());
            assertTrue(ItemStack.matches(item, decoded.items().get(0)));
            buffer.clear();
            var response = new WirelessJeiSupplyResultPacket(13, 22, 1, List.of(item));
            response.write(buffer);
            var restored = WirelessJeiSupplyResultPacket.read(buffer);
            assertEquals(1, restored.status());
            assertTrue(ItemStack.matches(item, restored.items().get(0)));
        } finally {
            buffer.release();
        }
    }

    @Test
    void supplyPacketsPreserveLargeCountsAndExactTags() {
        for (int count : new int[] {127, 128, 255, WirelessJeiInventoryPlan.MAX_COUNT}) {
            var stack = new ItemStack(Items.DIAMOND, count);
            stack.getOrCreateTag().putString("Origin", "ME");
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                new WirelessJeiSupplyPacket(13, count, true, List.of(stack)).write(buffer);
                var query = WirelessJeiSupplyPacket.read(buffer);
                assertTrue(ItemStack.matches(stack, query.items().get(0)), "request count " + count);
                buffer.clear();
                new WirelessJeiSupplyResultPacket(13, count, 1, List.of(stack)).write(buffer);
                var response = WirelessJeiSupplyResultPacket.read(buffer);
                assertTrue(ItemStack.matches(stack, response.items().get(0)), "response count " + count);
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void supplyPacketsRejectEmptyAndOutOfRangeCounts() {
        for (var stack : List.of(new ItemStack(Items.DIAMOND, WirelessJeiInventoryPlan.MAX_COUNT + 1),
                ItemStack.EMPTY)) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                assertThrows(IllegalArgumentException.class,
                        () -> new WirelessJeiSupplyPacket(13, 1, false, List.of(stack)).write(buffer));
                buffer.clear();
                assertThrows(IllegalArgumentException.class,
                        () -> new WirelessJeiSupplyResultPacket(13, 1, 1, List.of(stack)).write(buffer));
            } finally {
                buffer.release();
            }
        }
        for (int count : new int[] {-1, 0, WirelessJeiInventoryPlan.MAX_COUNT + 1}) {
            for (boolean response : new boolean[] {false, true}) {
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    buffer.writeVarInt(13);
                    buffer.writeVarInt(1);
                    if (response) buffer.writeVarInt(1);
                    else {
                        buffer.writeBoolean(false); // take
                        buffer.writeBoolean(false); // craft
                    }
                    buffer.writeVarInt(1);
                    buffer.writeItem(count == 0 ? ItemStack.EMPTY : new ItemStack(Items.DIAMOND));
                    buffer.writeVarInt(count);
                    if (response) assertThrows(IllegalArgumentException.class,
                            () -> WirelessJeiSupplyResultPacket.read(buffer));
                    else assertThrows(IllegalArgumentException.class, () -> WirelessJeiSupplyPacket.read(buffer));
                } finally {
                    buffer.release();
                }
            }
        }
    }
}
