package com.moakiee.ae2lt.mixin.neoecoae;

import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Runs the plugin's narrow transformation after NeoECO's land callback is merged.
 * LT's own lightning dispatch remains in ServerLevel, including modded subclasses.
 */
// NeoECO uses the default 1000 priority. Run our postApply after its handler merge.
@Mixin(value = LightningBolt.class, priority = 500)
public abstract class NeoEcoLightningTransformMixin {
}
