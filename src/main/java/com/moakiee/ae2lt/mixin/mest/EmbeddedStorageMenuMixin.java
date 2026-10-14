package com.moakiee.ae2lt.mixin.mest;

import appeng.menu.me.common.MEStorageMenu;
import com.moakiee.ae2lt.menu.extension.EmbeddedTerminalMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native crafting/encoding updates still run; only the outer menu scans and synchronizes ME. */
@Mixin(value = MEStorageMenu.class, remap = false)
public abstract class EmbeddedStorageMenuMixin {
    @Inject(method = "broadcastChanges", at = @At("HEAD"), cancellable = true)
    private void ae2lt$outerOwnsStorage(CallbackInfo ci) {
        if ((Object) this instanceof EmbeddedTerminalMenu embedded && embedded.outerMenu() != null) {
            var self = (MEStorageMenu) (Object) this;
            self.setLinkStatus(embedded.outerMenu().getLinkStatus());
            ci.cancel();
        }
    }
}
