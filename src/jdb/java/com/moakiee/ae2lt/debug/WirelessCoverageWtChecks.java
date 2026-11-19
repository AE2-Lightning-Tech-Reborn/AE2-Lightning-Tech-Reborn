package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import de.mari_023.ae2wtlib.AE2wtlibItems;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Kept out of API-only class loading. */
final class WirelessCoverageWtChecks {
    static void run(ServerPlayer player, GlobalPos anchor, WirelessOverloadedControllerBlockEntity controller) {
        for (var item : java.util.List.of(AE2wtlibItems.WIRELESS_CRAFTING_TERMINAL,
                AE2wtlibItems.PATTERN_ENCODING_TERMINAL, AE2wtlibItems.PATTERN_ACCESS_TERMINAL)) {
            WirelessControllerCoverageGameTests.connected(
                    WirelessControllerCoverageGameTests.host(player, new ItemStack(item), anchor), controller);
            if (item != AE2wtlibItems.PATTERN_ACCESS_TERMINAL) WirelessControllerCoverageGameTests.menuRoundTrip(player);
        }
        var terminal = new ItemStack(AE2wtlibItems.UNIVERSAL_TERMINAL);
        for (var definition : WTDefinition.wirelessTerminals()) {
            terminal.set(definition.componentType(), com.mojang.datafixers.util.Unit.INSTANCE);
        }
        for (var definition : WTDefinition.wirelessTerminals()) {
            player.getInventory().setItem(0, terminal);
            de.mari_023.ae2wtlib.api.terminal.WUTHandler.setCurrentTerminal(player,
                    appeng.menu.locator.MenuLocators.forInventorySlot(0), terminal, definition);
            WirelessControllerCoverageGameTests.connected(
                    WirelessControllerCoverageGameTests.host(player, terminal, anchor), controller);
            // Pattern access is a provider-list menu; its host is checked above.
            if (!definition.equals(de.mari_023.ae2wtlib.wut.WTDefinitions.PATTERN_ACCESS)) {
                WirelessControllerCoverageGameTests.menuRoundTrip(player);
            }
        }
    }
}
