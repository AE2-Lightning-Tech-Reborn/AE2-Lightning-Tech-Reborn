package com.moakiee.ae2lt.mixin.client;

import com.moakiee.ae2lt.item.FumoBlockItem;
import com.moakiee.ae2lt.util.NaturalLightningOrigin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningBoltRenderer.class)
public abstract class LightningBoltRendererMixin {
    @Unique
    private boolean ae2lt$tintedLightning;

    @Inject(method = "render(Lnet/minecraft/world/entity/LightningBolt;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void ae2lt$checkPigmee(LightningBolt bolt, float yaw, float partialTick, PoseStack poseStack,
                                  MultiBufferSource buffers, int light, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        // Check equipment once per bolt render, not once per vertex or segment.
        ae2lt$tintedLightning = player != null
                && ((NaturalLightningOrigin) bolt).ae2lt$isNaturalWeatherLightning()
                && FumoBlockItem.isWearingPigmee(player);
    }

    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LightningBolt;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LightningBoltRenderer;quad(Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFIFFFFFFFZZZZ)V"),
            index = 7, require = 4)
    private float ae2lt$tintRed(float original) {
        return ae2lt$tintedLightning ? 255 / 255.0F : original;
    }

    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LightningBolt;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LightningBoltRenderer;quad(Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFIFFFFFFFZZZZ)V"),
            index = 8, require = 4)
    private float ae2lt$tintGreen(float original) {
        return ae2lt$tintedLightning ? 186 / 255.0F : original;
    }

    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LightningBolt;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LightningBoltRenderer;quad(Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFIFFFFFFFZZZZ)V"),
            index = 9, require = 4)
    private float ae2lt$tintBlue(float original) {
        return ae2lt$tintedLightning ? 211 / 255.0F : original;
    }
}
