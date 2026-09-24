package com.moakiee.ae2lt.integration.ae2wtlib;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;

/** Loaded only when Curios is present. Keeps its actual component-bearing item as the menu anchor. */
public final class TianshuCuriosStackAccess {
    private TianshuCuriosStackAccess() {}

    @Nullable
    public static ItemStack locate(Player player, int slot) {
        var inventory = CuriosApi.getCuriosInventory(player).orElse(null);
        if (inventory == null) return null;
        var equipped = inventory.getEquippedCurios();
        if (slot < 0 || slot >= equipped.getSlots()) return null;
        var stack = equipped.getStackInSlot(slot);
        return TianshuWirelessIngredientSource.isTianshu(stack) ? stack : null;
    }
}
