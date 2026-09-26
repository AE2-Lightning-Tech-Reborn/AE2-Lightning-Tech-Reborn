package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorMaterials;
import com.moakiee.ae2lt.registry.ModDataComponents;

/**
 * Re-draws the energy lines of worn Celestweave pieces full-bright on top of the armor layer.
 * The glow breathes slowly and drops to an ember when a piece's FE buffer is empty.
 */
public final class CelestweaveArmorGlowLayer<T extends LivingEntity, M extends HumanoidModel<T>>
        extends RenderLayer<T, M> {
    private static final ResourceLocation OUTER_GLOW = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/models/armor/celestweave_layer_1_glow.png");
    private static final ResourceLocation INNER_GLOW = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/models/armor/celestweave_layer_2_glow.png");
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD};
    private static final float UNPOWERED_LEVEL = 0.25F;

    public CelestweaveArmorGlowLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch) {
        float pulse = 0.82F + 0.18F * Mth.sin(ageInTicks * 0.08F);
        for (EquipmentSlot slot : SLOTS) {
            float level = glowLevel(entity.getItemBySlot(slot), slot);
            if (level <= 0.0F) {
                continue;
            }
            HumanoidModel<LivingEntity> model = CelestweaveArmorModel.forSlot(slot);
            if (model == null) {
                continue;
            }
            copyPose(model);
            showSlot(model, slot);
            float brightness = level * pulse;
            model.renderToBuffer(
                    poseStack,
                    buffer.getBuffer(RenderType.eyes(slot == EquipmentSlot.LEGS ? INNER_GLOW : OUTER_GLOW)),
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    FastColor.ARGB32.colorFromFloat(1.0F, brightness, brightness, brightness));
        }
    }

    private static float glowLevel(ItemStack stack, EquipmentSlot slot) {
        if (!(stack.getItem() instanceof ArmorItem armor)
                || armor.getEquipmentSlot() != slot
                || !armor.getMaterial().is(CelestweaveArmorMaterials.CELESTWEAVE.getKey())) {
            return 0.0F;
        }
        if (stack.getItem() instanceof BaseCelestweaveArmorItem) {
            long stored = stack.getOrDefault(ModDataComponents.CELESTWEAVE_ENERGY_BUFFER.get(), 0L);
            return stored > 0L ? 1.0F : UNPOWERED_LEVEL;
        }
        // Phase-lock projections stand in for a powered private stack.
        return 1.0F;
    }

    @SuppressWarnings("unchecked")
    private void copyPose(HumanoidModel<LivingEntity> model) {
        getParentModel().copyPropertiesTo((HumanoidModel<T>) (HumanoidModel<?>) model);
    }

    private static void showSlot(HumanoidModel<LivingEntity> model, EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> {
            }
        }
    }
}
