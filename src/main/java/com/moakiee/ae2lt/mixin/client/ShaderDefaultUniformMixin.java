package com.moakiee.ae2lt.mixin.client;

import com.moakiee.ae2lt.client.ldlib.LdlibShaders;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the project's time uniforms at their original default-uniform sampling point. */
@Mixin(ShaderInstance.class)
public abstract class ShaderDefaultUniformMixin implements LdlibShaders.DefaultUniformUpdater {
    @Unique
    private Runnable ae2lt$defaultUniformUpdater;

    @Override
    public void ae2lt$setDefaultUniformUpdater(Runnable updater) {
        ae2lt$defaultUniformUpdater = updater;
    }

    @Inject(method = "setDefaultUniforms(Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/platform/Window;)V",
            at = @At("TAIL"))
    private void ae2lt$updateDefaultUniforms(CallbackInfo ci) {
        if (ae2lt$defaultUniformUpdater != null) {
            ae2lt$defaultUniformUpdater.run();
        }
    }
}
