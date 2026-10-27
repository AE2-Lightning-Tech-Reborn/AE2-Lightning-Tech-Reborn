package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.moakiee.ae2lt.logic.provider.OverloadedPatternProviderLogic;

/**
 * Keeps overload patterns out of normal AE2 pattern providers.
 * <p>
 * OverloadedPatternProviderLogic rebuilds its pattern list separately and still
 * accepts these patterns.
 */
@Mixin(value = PatternProviderLogic.class, priority = 400)
public abstract class PatternProviderLogicMixin {

    @Inject(method = "onStackReturnedToNetwork", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2lt$handleOverloadUnlockMatching(GenericStack genericStack, CallbackInfo ci) {
        if ((Object) this instanceof OverloadedPatternProviderLogic overloadedLogic
                && overloadedLogic.handleOverloadUnlockOnReturnedStack(genericStack)) {
            ci.cancel();
        }
    }
}
