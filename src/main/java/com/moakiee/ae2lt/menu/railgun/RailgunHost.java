package com.moakiee.ae2lt.menu.railgun;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.menuobjects.ItemMenuHost;


public class RailgunHost extends ItemMenuHost {
    public RailgunHost(Player player, int inventorySlot, ItemStack stack) {
        super(player, inventorySlot, stack);
    }

    public ItemStack getStack() {
        return getItemStack();
    }
}
