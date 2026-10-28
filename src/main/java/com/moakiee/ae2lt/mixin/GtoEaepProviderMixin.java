package com.moakiee.ae2lt.mixin;

import appeng.helpers.patternprovider.PatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;

/** Marker for adapting GTO's renamed dispatch method after all method merges. */
@Mixin(value = PatternProviderLogic.class, priority = 900, remap = false)
public abstract class GtoEaepProviderMixin {
}
