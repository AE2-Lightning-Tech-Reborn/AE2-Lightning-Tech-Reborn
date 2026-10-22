package com.moakiee.ae2lt.integration.ae2wtlib;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessCraftingTermMenuHost;
import com.moakiee.ae2lt.mixin.ae2wtlib.CraftingTerminalHandlerAccessor;
import de.mari_023.ae2wtlib.wct.WCTMenuHost;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetHost;
import net.minecraft.world.entity.player.Player;
/** A private native handler binds filters to the terminal being edited. */
public final class TianshuMagnetMenuHost extends WCTMenuHost {
    private final MagnetHost magnetHost;
    TianshuMagnetMenuHost(Player player, TianshuWirelessCraftingTermMenuHost terminal) {
        super(player, terminal.getPlayerInventorySlot(), terminal.getItemStack(), (p, menu) -> {});
        var handler = CraftingTerminalHandlerAccessor.ae2lt$create(player);
        var access = (CraftingTerminalHandlerAccessor) handler;
        access.ae2lt$setTerminal(terminal.getItemStack());
        access.ae2lt$setHost(terminal);
        magnetHost = new MagnetHost(handler);
    }
    public MagnetHost getBoundMagnetHost() { return magnetHost; }
}
