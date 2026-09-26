package com.moakiee.ae2lt.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;

/** Retains vanilla anvil combining rules while identifying this anvil for Forge repair events. */
public final class OverloadAlloyAnvilMenu extends AnvilMenu {
    public OverloadAlloyAnvilMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(id, inventory, access);
    }
}
