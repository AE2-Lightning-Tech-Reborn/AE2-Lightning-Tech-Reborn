package com.moakiee.ae2lt.network.jei;

import com.moakiee.ae2lt.logic.tianshu.terminal.WirelessJeiInventoryPlan;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

final class WirelessJeiItemCodec {
    private WirelessJeiItemCodec() {}

    static void write(FriendlyByteBuf buffer, ItemStack stack) {
        validate(stack, stack.getCount());
        buffer.writeItem(stack.copyWithCount(1));
        buffer.writeVarInt(stack.getCount());
    }

    static ItemStack read(FriendlyByteBuf buffer) {
        var stack = buffer.readItem();
        int count = buffer.readVarInt();
        validate(stack, count);
        return stack.copyWithCount(count);
    }

    private static void validate(ItemStack stack, int count) {
        if (stack.isEmpty() || count < 1 || count > WirelessJeiInventoryPlan.MAX_COUNT)
            throw new IllegalArgumentException("Invalid JEI item count");
    }
}
