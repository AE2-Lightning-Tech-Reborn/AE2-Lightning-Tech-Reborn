package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.me.common.RepoSlot;
import com.moakiee.ae2lt.client.machine.PigmeeSynthesisStationScreen;
import net.minecraft.client.Minecraft;
import org.anti_ad.mc.ipnext.event.LockSlotsHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep IPN's swipe operations from interpreting AE2 repository entries as real menu slots. */
@Pseudo
@Mixin(targets = "org.anti_ad.mc.ipnext.inventory.ContainerClicker", remap = false)
public abstract class IpnContainerClickerMixin {
    @Inject(method = {"shiftClick(I)V", "qClick(I)V"}, at = @At("HEAD"), cancellable = true)
    private void ae2lt$skipPigmeeRepositorySwipe(int slotIndex, CallbackInfo ci) {
        // IPN records the original Slot around its swipe callback, then sends only Slot.index.
        // RepoSlot.index is not a server slot ID (usually zero). Native AE2 screen clicks
        // already use the entry's serial; letting this second click through moves/drops an
        // unrelated hotbar item. Check the original object, never the numeric slot index:
        // real player slots, crafting slots and other screens must retain IPN behavior.
        if (Minecraft.getInstance().screen instanceof PigmeeSynthesisStationScreen
                && LockSlotsHandler.INSTANCE.getLastMouseClickSlot() instanceof RepoSlot) {
            ci.cancel();
        }
    }
}
