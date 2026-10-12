package com.moakiee.ae2lt.mixin.client;

import com.moakiee.ae2lt.item.FumoBlockItem;
import com.moakiee.ae2lt.registry.ModAttachmentTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

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
                && Boolean.TRUE.equals(bolt.getExistingDataOrNull(ModAttachmentTypes.NATURAL_LIGHTNING))
                && FumoBlockItem.isWearingPigmee(player);
    }

    @ModifyArgs(method = "render(Lnet/minecraft/world/entity/LightningBolt;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LightningBoltRenderer;quad(Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFIFFFFFFFZZZZ)V"),
            require = 4)
    private void ae2lt$tintNaturalLightning(Args args) {
        if (ae2lt$tintedLightning) {
            args.set(7, 255 / 255.0F);
            args.set(8, 186 / 255.0F);
            args.set(9, 211 / 255.0F);
        }
    }
}
