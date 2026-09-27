package com.moakiee.ae2lt.item;

import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWctIntegration;
import com.moakiee.ae2lt.integration.ae2wtlib.WirelessTerminalFrequencyLink;
import com.moakiee.ae2lt.menu.TianshuWirelessCraftingTermMenu;
import de.mari_023.ae2wtlib.terminal.ItemWT;
import de.mari_023.ae2wtlib.wut.ItemWUT;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class TianshuWirelessCraftingTerminalItem extends ItemWT {
    @Override public MenuType<?> getMenuType() { return TianshuWirelessCraftingTermMenu.TYPE; }
    @Override public MenuType<?> getMenuType(ItemStack stack) { return TianshuWirelessCraftingTermMenu.TYPE; }
    @Override public String getDescriptionId() { return "item.ae2lt.wireless_tianshu_crafting_terminal"; }
    @Override public boolean checkUniversalPreconditions(ItemStack stack, Player player) {
        if (stack.isEmpty() || player.level().isClientSide() || (stack.getItem() != this && !(stack.getItem() instanceof ItemWUT))) return false;
        var route = WirelessTerminalFrequencyLink.resolveRoute(player, stack);
        return route.usesFrequencyRoute() ? route.isNetworkPowered() : super.checkUniversalPreconditions(stack, player);
    }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (entity instanceof ServerPlayer player) TianshuWctIntegration.tick(player);
    }
}
