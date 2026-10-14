package com.moakiee.ae2lt.mixin.client;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import com.moakiee.ae2lt.compat.gto.GtoTerminalAmounts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AEKey.class, remap = false)
public abstract class GtoTerminalKeyAmountMixin {
    @Inject(method = "formatAmount", at = @At("HEAD"), cancellable = true)
    private void ae2lt$exactSlot(long amount, AmountFormat format, CallbackInfoReturnable<String> cir) {
        var exact = GtoTerminalAmounts.format((AEKey) (Object) this, format);
        if (exact != null) cir.setReturnValue(exact);
    }
}
