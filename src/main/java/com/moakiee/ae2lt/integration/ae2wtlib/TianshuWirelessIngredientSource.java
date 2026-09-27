package com.moakiee.ae2lt.integration.ae2wtlib;

import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessCraftingTermMenuHost;
import de.mari_023.ae2wtlib.wut.WTDefinition;
import de.mari_023.ae2wtlib.terminal.ItemWT;
import de.mari_023.ae2wtlib.wut.WUTHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Uses the library's inventory/Curios locator, including a Tianshu module inside a WUT. */
public final class TianshuWirelessIngredientSource {
    private TianshuWirelessIngredientSource() {}

    public static List<MenuLocator> locate(Player player) {
        var result = new ArrayList<MenuLocator>();
        for (var name : List.of(Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME, Ae2wtlibIntegration.TIANSHU_TERMINAL_NAME)) {
            var locator = de.mari_023.ae2wtlib.Platform.findTerminalFromAccessory(player, name);
            if (locator != null && !result.contains(locator)) result.add(locator);
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isTianshu(player.getInventory().getItem(i))) result.add(MenuLocators.forInventorySlot(i));
        }
        return result;
    }

    private static boolean isTianshu(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemWT)) return false;
        for (var name : List.of(Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME, Ae2wtlibIntegration.TIANSHU_TERMINAL_NAME)) {
            if (WUTHandler.wirelessTerminals.containsKey(name) && WUTHandler.hasTerminal(stack, name)) return true;
        }
        return false;
    }

    @Nullable
    public static TianshuWirelessCraftingTermMenuHost open(Player player, MenuLocator locator) {
        var stack = WUTHandler.getItemStackFromLocator(player, locator);
        if (!isTianshu(stack)) return null;
        Integer inventorySlot = null;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i) == stack) { inventorySlot = i; break; }
        }
        var host = new TianshuWirelessCraftingTermMenuHost(player, inventorySlot, stack, (p, menu) -> {});
        var node = host.getActionableNode();
        return host.stillValid() && host.rangeCheck() && node != null
                && node.getGrid().getEnergyService().isNetworkPowered() ? host : null;
    }
}
