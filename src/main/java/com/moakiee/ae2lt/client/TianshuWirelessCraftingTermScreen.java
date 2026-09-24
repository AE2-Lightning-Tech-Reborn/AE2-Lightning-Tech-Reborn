package com.moakiee.ae2lt.client;

import appeng.client.gui.style.ScreenStyle;
import com.moakiee.ae2lt.menu.TianshuWirelessCraftingTermMenu;
import de.mari_023.ae2wtlib.api.gui.ScrollingUpgradesPanel;
import de.mari_023.ae2wtlib.api.terminal.IUniversalTerminalCapable;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class TianshuWirelessCraftingTermScreen<M extends TianshuWirelessCraftingTermMenu>
        extends TianshuCraftingTermScreen<M> implements IUniversalTerminalCapable {
    private final ScrollingUpgradesPanel upgradesPanel;
    public TianshuWirelessCraftingTermScreen(M menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        if (menu.isWUT()) addTerminalSelectionPanel(widgets);
        upgradesPanel = addUpgradePanel(widgets, menu);
    }
    @Override public void init() { super.init(); upgradesPanel.setMaxRows(Math.max(2, getVisibleRows())); }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        return super.keyPressed(event) || checkForTerminalKeys(event);
    }
    @Override public WTMenuHost getHost() { return getMenu().getWirelessHost(); }
}
