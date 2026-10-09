package com.moakiee.ae2lt.integration.ae2wtlib;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessCraftingTermMenuHost;
import com.moakiee.ae2lt.mixin.ae2wtlib.BoundCraftingTerminalHandlerAccessor;
import de.mari_023.ae2wtlib.wct.WCTMenuHost;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetHost;
import net.minecraft.world.entity.player.Player;
public final class TianshuMagnetMenuHost extends WCTMenuHost {
    private final MagnetHost magnetHost;
    TianshuMagnetMenuHost(Player player,TianshuWirelessCraftingTermMenuHost terminal) {
        super(player,null,terminal.getItemStack(),(p,menu)->{});
        var handler=BoundCraftingTerminalHandlerAccessor.create(player);
        ((BoundCraftingTerminalHandlerAccessor)handler).setTerminal(terminal.getItemStack());
        magnetHost=new MagnetHost(handler);
    }
    public MagnetHost getBoundMagnetHost() {return magnetHost;}
}
