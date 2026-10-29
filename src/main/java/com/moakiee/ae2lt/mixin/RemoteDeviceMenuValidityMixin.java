package com.moakiee.ae2lt.mixin;

import appeng.menu.AEBaseMenu;
import com.moakiee.ae2lt.logic.management.RemoteDeviceLocator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AEBaseMenu.class, remap = false)
public abstract class RemoteDeviceMenuValidityMixin {
    @Inject(method = "isValidMenu", at = @At("HEAD"), cancellable = true)
    private void ae2lt$validateRemoteSession(CallbackInfoReturnable<Boolean> cir) {
        var menu = (AEBaseMenu) (Object) this;
        if (menu.getLocator() instanceof RemoteDeviceLocator remote && !remote.valid(menu.getPlayer())) cir.setReturnValue(false);
    }
}
