package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.me.common.RepoSlot;
import com.moakiee.ae2lt.client.PigmeeSynthesisStationScreen;
import net.minecraft.client.Minecraft;
import org.anti_ad.mc.ipnext.event.LockSlotsHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.anti_ad.mc.ipnext.inventory.ContainerClicker", remap = false)
public abstract class IpnContainerClickerMixin {
    @Inject(method = {"shiftClick(I)V", "qClick(I)V"}, at = @At("HEAD"), cancellable = true)
    private void ae2lt$skipPigmeeRepositorySwipe(int slotIndex, CallbackInfo callback) {
        if (Minecraft.getInstance().screen instanceof PigmeeSynthesisStationScreen
                && LockSlotsHandler.INSTANCE.getLastMouseClickSlot() instanceof RepoSlot) {
            callback.cancel();
        }
    }
}
