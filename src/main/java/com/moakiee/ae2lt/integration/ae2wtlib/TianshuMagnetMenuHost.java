package com.moakiee.ae2lt.integration.ae2wtlib;

import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessCraftingTermMenuHost;
import de.mari_023.ae2wtlib.terminal.ItemWT;
import de.mari_023.ae2wtlib.wct.CraftingTerminalHandler;
import de.mari_023.ae2wtlib.wct.WCTMenuHost;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetHost;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Binds the native magnet menu to the terminal being edited instead of the global first terminal. */
public final class TianshuMagnetMenuHost extends WCTMenuHost {
    private final MagnetHost magnetHost;

    TianshuMagnetMenuHost(Player player, TianshuWirelessCraftingTermMenuHost terminalHost) {
        super(player, terminalHost.getSlot(), terminalHost.getItemStack(), (p, menu) -> {});
        var handler = com.moakiee.ae2lt.mixin.ae2wtlib.CraftingTerminalHandlerAccessor.ae2lt$create(player);
        ((BoundCraftingTerminalHandler) handler).ae2lt$bindTerminal(terminalHost.getItemStack());
        magnetHost = new MagnetHost(handler);
    }

    public MagnetHost getBoundMagnetHost() { return magnetHost; }
}
