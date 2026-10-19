package com.moakiee.ae2lt.compat;
import net.minecraft.world.item.ItemStack;
public final class LegacyStacks {
    private LegacyStacks() {}
    public static ItemStack copyWithCount(ItemStack stack, int count) {
        var copy = stack.copy();
        copy.setCount(count);
        return copy;
    }
}
