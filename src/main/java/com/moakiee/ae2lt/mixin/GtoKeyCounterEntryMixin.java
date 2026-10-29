package com.moakiee.ae2lt.mixin;

import appeng.api.stacks.KeyCounter;
import org.spongepowered.asm.mixin.Mixin;

/** The config plugin adapts entries only when a legacy optional consumer is installed. */
@Mixin(KeyCounter.class)
public abstract class GtoKeyCounterEntryMixin { }
