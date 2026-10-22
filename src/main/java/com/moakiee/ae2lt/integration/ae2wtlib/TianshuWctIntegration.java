package com.moakiee.ae2lt.integration.ae2wtlib;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessCraftingTermMenuHost;
import com.moakiee.ae2lt.menu.TianshuWirelessCraftingTermMenu;
import de.mari_023.ae2wtlib.wut.WUTHandler;
import de.mari_023.ae2wtlib.wct.CraftingTerminalHandler;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetHandler;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
public final class TianshuWctIntegration {
    private TianshuWctIntegration() {}
    public static TianshuWirelessCraftingTermMenu createMenu(int id, Inventory inv, TianshuWirelessCraftingTermMenuHost host) {
        return new TianshuEnhancedWirelessCraftingMenu(id, inv, host);
    }
    public static boolean hasTianshuCrafting(ItemStack stack) {
        return WUTHandler.hasTerminal(stack, Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        var selected = CraftingTerminalHandler.getCraftingTerminalHandler(player).getCraftingTerminal();
        if (hasTianshuCrafting(selected)) MagnetHandler.handle(player, selected);
    }
}
