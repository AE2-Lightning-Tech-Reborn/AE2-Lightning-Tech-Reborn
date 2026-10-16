package com.moakiee.ae2lt.mixin.appflux;

import appeng.helpers.patternprovider.PatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "com.extendedae_plus.compat.UpgradeSlotCompat", remap = false)
public abstract class ExtendedAePlusUpgradeCallbackMixin {
    @ModifyVariable(
            method = {"resolvePatternProviderAppfluxUpgradesField", "resolvePatternProviderAppfluxUpgradesChangedMethod"},
            at = @At("HEAD"), argsOnly = true, require = 0)
    private static Class<?> ae2lt$resolveOnProviderBase(Class<?> providerType) {
        // AppFlux adds private members to the base; the addon caches one declared-member lookup globally.
        return PatternProviderLogic.class;
    }
}
