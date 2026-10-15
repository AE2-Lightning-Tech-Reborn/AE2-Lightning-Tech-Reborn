package com.moakiee.ae2lt.mixin;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moakiee.ae2lt.logic.management.RemoteDeviceSession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MenuOpener.class)
public abstract class RemoteDeviceMenuOpenerMixin {
    @WrapMethod(method = "open(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/world/entity/player/Player;Lappeng/menu/locator/MenuHostLocator;Z)Z")
    private static boolean ae2lt$remoteHost(MenuType<?> type, Player player, MenuHostLocator locator,
            boolean returning, Operation<Boolean> original) {
        var remote = RemoteDeviceSession.opening();
        if (remote == null) {
            var current = RemoteDeviceSession.locator(player);
            if (current != null && (returning || locator == current)) remote = current;
        }
        if (remote != null) {
            if (!remote.valid(player)) return false;
            locator = remote;
        }
        return original.call(type, player, locator, returning);
    }
}
