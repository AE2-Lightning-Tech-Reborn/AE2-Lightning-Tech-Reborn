package com.moakiee.ae2lt.network.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moakiee.ae2lt.logic.tianshu.terminal.WirelessJeiInventoryPlan;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WirelessJeiSupplyPacketTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void requestAndOfferPreserveLargeTaggedCounts() {
        var stack = new ItemStack(Items.OAK_PLANKS, WirelessJeiInventoryPlan.MAX_COUNT);
        stack.getOrCreateTag().putString("sample", "variant");
        var request = new WirelessJeiSupplyPacket(7, 29, true, List.of(stack));
        var offer = new WirelessJeiSupplyResultPacket(7, 29, 1, List.of(stack));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            request.write(buffer);
            var decodedRequest = WirelessJeiSupplyPacket.read(buffer);
            assertEquals(WirelessJeiInventoryPlan.MAX_COUNT, decodedRequest.items().get(0).getCount());
            assertTrue(ItemStack.isSameItemSameTags(stack, decodedRequest.items().get(0)));
            assertFalse(buffer.isReadable());
            buffer.clear();
            offer.write(buffer);
            var decodedOffer = WirelessJeiSupplyResultPacket.read(buffer);
            assertEquals(WirelessJeiInventoryPlan.MAX_COUNT, decodedOffer.items().get(0).getCount());
            assertTrue(ItemStack.isSameItemSameTags(stack, decodedOffer.items().get(0)));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsOutOfRangeCountsAndEntries() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(1);
            buffer.writeItem(new ItemStack(Items.STONE));
            buffer.writeVarInt(WirelessJeiInventoryPlan.MAX_COUNT + 1);
            assertThrows(IllegalArgumentException.class, () -> WirelessJeiStackCodec.read(buffer));
            buffer.clear();
            buffer.writeVarInt(WirelessJeiInventoryPlan.MAX_ENTRIES + 1);
            assertThrows(IllegalArgumentException.class, () -> WirelessJeiStackCodec.read(buffer));
            var tooMany = new ArrayList<ItemStack>();
            for (int index = 0; index <= WirelessJeiInventoryPlan.MAX_ENTRIES; index++) {
                tooMany.add(new ItemStack(Items.STONE));
            }
            assertThrows(IllegalArgumentException.class, () -> WirelessJeiStackCodec.write(buffer, tooMany));
        } finally {
            buffer.release();
        }
    }

    @Test
    void inventoryProjectionDoesNotMutateInput() {
        var inventory = new ArrayList<ItemStack>();
        for (int index = 0; index < 36; index++) inventory.add(ItemStack.EMPTY);
        var request = new ItemStack(Items.STONE, WirelessJeiInventoryPlan.MAX_COUNT);
        var result = WirelessJeiInventoryPlan.insert(inventory, List.of(request));
        assertEquals(0, inventory.stream().mapToInt(ItemStack::getCount).sum());
        assertEquals(WirelessJeiInventoryPlan.MAX_COUNT, request.getCount());
        assertEquals(WirelessJeiInventoryPlan.MAX_COUNT,
                result.stream().mapToInt(ItemStack::getCount).sum());
        assertTrue(result.stream().allMatch(stack -> stack.getCount() == 64));
    }
}
