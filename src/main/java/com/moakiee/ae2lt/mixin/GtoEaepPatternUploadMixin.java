package com.moakiee.ae2lt.mixin;

import appeng.menu.me.items.PatternEncodingTermMenu;
import com.moakiee.ae2lt.compat.gto.GtoEaepPatternEncodingSupport;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.extendedae_plus.util.uploadPattern.MatrixUploadUtil", remap = false)
public abstract class GtoEaepPatternUploadMixin {
    @Inject(method = "refundBlankPattern", at = @At("HEAD"), cancellable = true)
    private static void ae2lt$refundToGtoStorage(ServerPlayer player, PatternEncodingTermMenu menu, int count, CallbackInfo ci) {
        GtoEaepPatternEncodingSupport.refund(menu, count);
        ci.cancel();
    }
}
