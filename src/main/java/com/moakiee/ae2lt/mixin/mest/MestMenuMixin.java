package com.moakiee.ae2lt.mixin.mest;

import com.lhy.mest.terminal.MESTMenu;
import com.moakiee.ae2lt.integration.mest.MestTianshuAccess;
import com.moakiee.ae2lt.integration.mest.MestTianshuSession;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MESTMenu.class, remap = false)
public abstract class MestMenuMixin implements MestTianshuAccess {
    @Unique private MestTianshuSession ae2lt$session;
    @Override public MestTianshuSession ae2lt$tianshuSession() {
        if (ae2lt$session == null) ae2lt$session = new MestTianshuSession((MESTMenu) (Object) this);
        return ae2lt$session;
    }
    @Inject(method = "broadcastChanges", at = @At("RETURN"))
    private void ae2lt$broadcast(CallbackInfo ci) {
        if (ae2lt$session != null) ae2lt$session.broadcast();
    }
    @Inject(method = "removed", at = @At("HEAD"))
    private void ae2lt$close(Player player, CallbackInfo ci) {
        if (ae2lt$session != null) ae2lt$session.close();
    }
}
