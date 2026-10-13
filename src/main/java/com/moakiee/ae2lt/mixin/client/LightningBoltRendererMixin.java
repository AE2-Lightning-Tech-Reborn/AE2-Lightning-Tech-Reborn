package com.moakiee.ae2lt.mixin.client;

import com.moakiee.ae2lt.item.FumoBlockItem;
import com.moakiee.ae2lt.registry.ModAttachmentTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
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
    private static final ContextKey<Boolean> AE2LT_PIGMEE_LIGHTNING =
            new ContextKey<>(Identifier.parse("ae2lt:pigmee_lightning"));

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LightningBolt;Lnet/minecraft/client/renderer/entity/state/LightningBoltRenderState;F)V",
            at = @At("TAIL"))
    private void ae2lt$checkPigmee(LightningBolt bolt, LightningBoltRenderState state,
            float partialTick, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        state.setRenderData(AE2LT_PIGMEE_LIGHTNING, player != null
                && Boolean.TRUE.equals(bolt.getExistingDataOrNull(ModAttachmentTypes.NATURAL_LIGHTNING))
                && FumoBlockItem.isWearingPigmee(player));
    }

    @ModifyArgs(method = "lambda$submit$0", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/LightningBoltRenderer;quad(Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFIFFFFFFFZZZZ)V"),
            require = 4)
    private static void ae2lt$tintNaturalLightning(Args args, LightningBoltRenderState state,
            float[] xOffsets, float xOffset, float[] zOffsets, float zOffset,
            PoseStack.Pose pose, VertexConsumer consumer) {
        // Deferred geometry reads its own state instead of a renderer-wide flag.
        if (Boolean.TRUE.equals(state.getRenderData(AE2LT_PIGMEE_LIGHTNING))) {
            args.set(7, 1.0F);
            args.set(8, 186 / 255.0F);
            args.set(9, 211 / 255.0F);
        }
    }
}
