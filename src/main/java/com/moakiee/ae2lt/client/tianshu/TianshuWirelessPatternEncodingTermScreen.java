package com.moakiee.ae2lt.client.tianshu;

import appeng.client.gui.style.ScreenStyle;
import com.moakiee.ae2lt.integration.ae2wtlib.client.WirelessUpgradesPanel;
import de.mari_023.ae2wtlib.api.terminal.IUniversalTerminalCapable;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Tianshu screen variant with wireless-terminal selector and upgrade panel support. */
public final class TianshuWirelessPatternEncodingTermScreen
        extends TianshuPatternEncodingTermScreen<TianshuWirelessPatternEncodingTermMenu>
        implements IUniversalTerminalCapable {
    private final WirelessUpgradesPanel upgradesPanel;

    public TianshuWirelessPatternEncodingTermScreen(
            TianshuWirelessPatternEncodingTermMenu menu,
            Inventory inventory,
            Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        if (menu.isWUT()) {
            addToLeftToolbar(cycleTerminalButton());
        }
        upgradesPanel = WirelessUpgradesPanel.attach(widgets, menu, getHost());
    }

    @Override
    public void init() {
        super.init();
        upgradesPanel.setMaxRows(Math.max(2, getVisibleRows()));
    }

    @Override
    public void removed() {
        try {
            upgradesPanel.onRemoved();
        } finally {
            super.removed();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        return handled || checkForTerminalKeys(keyCode, scanCode);
    }

    @Override
    public WTMenuHost getHost() {
        return (WTMenuHost) getMenu().getHost();
    }

}
