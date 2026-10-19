package com.moakiee.ae2lt.compat;

import appeng.api.inventories.InternalInventory;
import net.minecraft.world.item.ItemStack;

public final class LegacyInventories {
    private LegacyInventories() {}
    public static void clear(InternalInventory inventory) {
        for (int i = 0; i < inventory.size(); i++) inventory.setItemDirect(i, ItemStack.EMPTY);
    }
}
