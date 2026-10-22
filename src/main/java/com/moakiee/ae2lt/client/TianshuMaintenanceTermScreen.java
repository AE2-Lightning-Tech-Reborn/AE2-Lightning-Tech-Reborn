package com.moakiee.ae2lt.client;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.guidebook.PageAnchor;
import appeng.menu.me.common.MEStorageMenu;
import com.moakiee.ae2lt.menu.TianshuMaintenanceMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Compatibility entry point for the terminal screen moved to the tianshu package. */
public abstract class TianshuMaintenanceTermScreen<M extends MEStorageMenu & TianshuMaintenanceMenu>
        extends com.moakiee.ae2lt.client.tianshu.TianshuMaintenanceTermScreen<M> {
    static PageAnchor terminalHelpTopic(String fragment) {
        return new PageAnchor(new ResourceLocation(
                "ae2lt", "terminals/pattern-encoding-terminal.md"), fragment);
    }

    protected TianshuMaintenanceTermScreen(M menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }
}
