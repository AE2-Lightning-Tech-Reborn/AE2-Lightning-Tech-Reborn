package com.moakiee.ae2lt.mixin.client;

import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.locator.MenuLocator;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.moakiee.ae2lt.logic.management.RemoteDeviceLocator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = MenuTypeBuilder.class, remap = false)
public abstract class RemoteDeviceClientMenuMixin {
    @ModifyReturnValue(method = "fromNetwork", at = @At("RETURN"))
    private AEBaseMenu ae2lt$rememberRemoteHost(AEBaseMenu menu, @Local MenuLocator locator) {
        if (locator instanceof RemoteDeviceLocator) menu.setLocator(locator);
        return menu;
    }
}
