package com.moakiee.ae2lt.integration.ae2wtlib;

import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import de.mari_023.ae2wtlib.terminal.ItemWT;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
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
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ItemStack, Boolean>());
        for (var name : List.of(Ae2wtlibIntegration.TIANSHU_TERMINAL_NAME,
                Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME)) {
            var accessory = de.mari_023.ae2wtlib.Platform.findTerminalFromAccessory(player, name);
            if (accessory != null && seen.add(WUTHandler.getItemStackFromLocator(player, accessory))) result.add(accessory);
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isTianshu(player.getInventory().getItem(i))) result.add(MenuLocators.forInventorySlot(i));
        }
        return result;
    }

    private static boolean isTianshu(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemWT)) return false;
        for (var name : List.of(Ae2wtlibIntegration.TIANSHU_TERMINAL_NAME,
                Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME)) {
            if (WUTHandler.wirelessTerminals.containsKey(name) && WUTHandler.hasTerminal(stack, name)) return true;
        }
        return false;
    }

    @Nullable
    public static WTMenuHost open(Player player, MenuLocator locator) {
        var stack = WUTHandler.getItemStackFromLocator(player, locator);
        if (!isTianshu(stack)) return null;
        Integer inventorySlot = null;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i) == stack) { inventorySlot = i; break; }
        }
        // Ingredient supply needs only the wireless connection and energy, not either terminal's editor.
        var host = new WTMenuHost(player, inventorySlot, stack, (p, menu) -> {}) {
            @Override
            public boolean stillValid() {
                return getItemStack() == stack && super.stillValid();
            }
        };
        var node = host.getActionableNode();
        return host.stillValid() && host.rangeCheck() && node != null
                && node.getGrid().getEnergyService().isNetworkPowered() ? host : null;
    }
}
