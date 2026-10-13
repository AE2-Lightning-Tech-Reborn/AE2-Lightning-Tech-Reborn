package com.moakiee.ae2lt.mixin.client;

import com.moakiee.ae2lt.client.workstation.PigmeeControlScreen;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Observation only. Do not change Minecraft.cameraEntity or LocalPlayer's native tick/packets. */
@Mixin(Camera.class)
public abstract class PigmeeRenderCameraMixin {
    @ModifyVariable(method = "setup", at = @At("HEAD"), argsOnly = true)
    private Entity ae2lt$pigmeeRenderSubject(Entity original) {
        return PigmeeControlScreen.renderView(original);
    }
}
