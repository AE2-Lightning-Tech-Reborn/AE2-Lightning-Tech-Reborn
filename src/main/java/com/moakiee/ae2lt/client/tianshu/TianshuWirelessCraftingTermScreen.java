package com.moakiee.ae2lt.client.tianshu;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.BackgroundPanel;
import com.moakiee.ae2lt.menu.TianshuWirelessCraftingTermMenu;
import de.mari_023.ae2wtlib.wut.CycleTerminalButton;
import de.mari_023.ae2wtlib.wut.IUniversalTerminalCapable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public class TianshuWirelessCraftingTermScreen<M extends TianshuWirelessCraftingTermMenu>
        extends TianshuCraftingTermScreen<M> implements IUniversalTerminalCapable {
    public TianshuWirelessCraftingTermScreen(M menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu,inventory,title,style);
        if (menu.isWUT()) addToLeftToolbar(new CycleTerminalButton(ignored -> cycleTerminal()));
        widgets.add("singularityBackground", new BackgroundPanel(style.getImage("singularityBackground")));
    }
}
