package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.CelestweaveCoreItem;
import com.moakiee.ae2lt.item.PhaseLockProjectionItem;

/** Materializes Celestweave phase wings only while phase-wing flight is active. */
public final class PhaseWingLayer
        extends ElytraLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation WING_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/entity/celestweave_phase_wing.png");
    private static final ResourceLocation WING_GLOW = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/entity/celestweave_phase_wing_glow.png");

    private final ElytraModel<AbstractClientPlayer> glowModel;

    public PhaseWingLayer(PlayerRenderer renderer, EntityModelSet models) {
        super(renderer, models);
        this.glowModel = new ElytraModel<>(models.bakeLayer(ModelLayers.ELYTRA));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
            AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch) {
        super.render(poseStack, buffer, packedLight, player, limbSwing, limbSwingAmount, partialTick,
                ageInTicks, netHeadYaw, headPitch);
        if (!shouldRender(player.getItemBySlot(EquipmentSlot.CHEST), player) || usesSkinWings(player)) {
            return;
        }
        float brightness = 0.8F + 0.2F * Mth.sin(ageInTicks * 0.25F);
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, 0.125F);
        getParentModel().copyPropertiesTo(glowModel);
        glowModel.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        glowModel.renderToBuffer(
                poseStack,
                buffer.getBuffer(RenderType.eyes(WING_GLOW)),
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                FastColor.ARGB32.colorFromFloat(1.0F, brightness, brightness, brightness));
        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(ItemStack stack, AbstractClientPlayer player) {
        return player.isFallFlying()
                && isCelestweaveChest(stack);
    }

    @Override
    public ResourceLocation getElytraTexture(ItemStack stack, AbstractClientPlayer player) {
        return WING_TEXTURE;
    }

    /** ElytraLayer prefers skin elytra and capes; the glow only matches our own wing texture. */
    private static boolean usesSkinWings(AbstractClientPlayer player) {
        PlayerSkin skin = player.getSkin();
        return skin.elytraTexture() != null
                || skin.capeTexture() != null && player.isModelPartShown(PlayerModelPart.CAPE);
    }

    private static boolean isCelestweaveChest(ItemStack stack) {
        if (stack.getItem() instanceof CelestweaveCoreItem) {
            return true;
        }
        return stack.getItem() instanceof PhaseLockProjectionItem projection
                && projection.equipmentSlot() == EquipmentSlot.CHEST;
    }
}
