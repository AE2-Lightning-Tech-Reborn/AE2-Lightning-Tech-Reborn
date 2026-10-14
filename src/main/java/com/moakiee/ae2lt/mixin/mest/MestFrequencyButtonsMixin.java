package com.moakiee.ae2lt.mixin.mest;

import com.lhy.mest.client.MESTScreen;
import com.lhy.mest.client.dock.PanelSideBar;
import com.moakiee.ae2lt.integration.mest.client.MestFrequencyButtons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.lhy.mest.client.MestAddonUpgradeButtons", remap = false)
public abstract class MestFrequencyButtonsMixin {
    @Shadow private Object lightning;
    @Inject(method = "installLightning", at = @At("HEAD"), cancellable = true)
    private static void ae2lt$rebornButtons(MESTScreen screen, PanelSideBar sideBar, CallbackInfoReturnable<Object> cir) {
        cir.setReturnValue(new MestFrequencyButtons(sideBar));
    }
    @Inject(method = "updateLightning", at = @At("HEAD"), cancellable = true)
    private void ae2lt$update(MESTScreen screen, CallbackInfo ci) {
        ((MestFrequencyButtons) lightning).update(screen); ci.cancel();
    }
}
