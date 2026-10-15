package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.AEBaseScreen;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.moakiee.ae2lt.client.gui.RemoteDeviceMenuNavigation;
import java.util.List;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep recipe-viewer overlays clear of the separate navigation button. */
@Mixin(AEBaseScreen.class)
public abstract class RemoteDeviceScreenExclusionMixin {
    @ModifyReturnValue(method = "getExclusionZones", at = @At("RETURN"))
    private List<Rect2i> ae2lt$includeRemoteReturn(List<Rect2i> zones) {
        return RemoteDeviceMenuNavigation.includeReturnButton((AEBaseScreen<?>) (Object) this, zones);
    }
}
