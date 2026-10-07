package com.moakiee.ae2lt.network.jei;

import com.moakiee.ae2lt.logic.tianshu.terminal.WirelessJeiInventoryPlan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

final class WirelessJeiStackCodec {
    private WirelessJeiStackCodec() {}

    static void write(FriendlyByteBuf buffer, List<ItemStack> items) {
        if (items.size() > WirelessJeiInventoryPlan.MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many JEI inputs");
        }
        buffer.writeVarInt(items.size());
        for (var stack : items) {
            if (stack.isEmpty() || stack.getCount() > WirelessJeiInventoryPlan.MAX_COUNT) {
                throw new IllegalArgumentException("Invalid JEI input count");
            }
            buffer.writeItem(stack.copyWithCount(1));
            buffer.writeVarInt(stack.getCount());
        }
    }

    static List<ItemStack> read(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > WirelessJeiInventoryPlan.MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many JEI inputs");
        }
        var stacks = new ArrayList<ItemStack>(size);
        for (int index = 0; index < size; index++) {
            var stack = buffer.readItem();
            int count = buffer.readVarInt();
            if (stack.isEmpty() || count <= 0 || count > WirelessJeiInventoryPlan.MAX_COUNT) {
                throw new IllegalArgumentException("Invalid JEI input count");
            }
            stacks.add(stack.copyWithCount(count));
        }
        return List.copyOf(stacks);
    }
}
