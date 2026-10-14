package com.moakiee.ae2lt.mixin.mest;

import com.moakiee.ae2lt.integration.mest.client.MestTianshuClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MultiPlayerGameMode.class)
public abstract class EmbeddedVanillaClickMixin {
    @ModifyVariable(method = "handleInventoryMouseClick", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int ae2lt$mapSlot(int slot) { return MestTianshuClient.mapSlot(slot); }
}
