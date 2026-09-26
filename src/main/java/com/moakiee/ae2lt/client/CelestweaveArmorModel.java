package com.moakiee.ae2lt.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import com.moakiee.ae2lt.AE2LightningTech;

/**
 * Worn Celestweave armor: vanilla humanoid armor shells plus plated add-ons. Texture offsets
 * and sizes are shared with scripts/generate_celestweave_armor.py, which paints the 128x64 sheets.
 */
public final class CelestweaveArmorModel {
    public static final ModelLayerLocation OUTER_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "celestweave_armor"), "outer");
    public static final ModelLayerLocation INNER_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "celestweave_armor"), "inner");

    private static final int TEXTURE_WIDTH = 128;
    private static final int TEXTURE_HEIGHT = 64;
    private static final float FIN_SWEEP = 0.35F;

    @Nullable
    private static HumanoidModel<LivingEntity> outer;
    @Nullable
    private static HumanoidModel<LivingEntity> inner;

    private CelestweaveArmorModel() {
    }

    /** Helmet, chestplate and boots; same inflation as vanilla outer armor. */
    public static LayerDefinition createOuterLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(1.0F), 0.0F);
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.getChild("head");
        head.addOrReplaceChild("crest",
                CubeListBuilder.create().texOffs(64, 0).addBox(-0.5F, -9.75F, -2.0F, 1, 1, 6),
                PartPose.ZERO);
        head.addOrReplaceChild("right_ear_fin",
                CubeListBuilder.create().texOffs(80, 0).addBox(-0.5F, -1.5F, -1.0F, 1, 3, 5),
                PartPose.offsetAndRotation(-5.25F, -7.0F, 0.0F, FIN_SWEEP, 0.0F, 0.0F));
        head.addOrReplaceChild("left_ear_fin",
                CubeListBuilder.create().texOffs(80, 0).mirror().addBox(-0.5F, -1.5F, -1.0F, 1, 3, 5),
                PartPose.offsetAndRotation(5.25F, -7.0F, 0.0F, FIN_SWEEP, 0.0F, 0.0F));
        head.addOrReplaceChild("brow_gem",
                CubeListBuilder.create().texOffs(92, 0).addBox(-1.0F, -8.25F, -5.75F, 2, 2, 1),
                PartPose.ZERO);

        PartDefinition body = root.getChild("body");
        body.addOrReplaceChild("heart_core",
                CubeListBuilder.create().texOffs(98, 0).addBox(-1.5F, 2.0F, -3.75F, 3, 3, 1),
                PartPose.ZERO);
        body.addOrReplaceChild("back_pack",
                CubeListBuilder.create().texOffs(64, 10).addBox(-3.0F, 1.0F, 2.5F, 6, 7, 2),
                PartPose.ZERO);

        root.getChild("right_arm").addOrReplaceChild("pauldron",
                CubeListBuilder.create().texOffs(64, 20).addBox(-4.5F, -3.75F, -3.5F, 6, 4, 7),
                PartPose.ZERO);
        root.getChild("left_arm").addOrReplaceChild("pauldron",
                CubeListBuilder.create().texOffs(64, 20).mirror().addBox(-1.5F, -3.75F, -3.5F, 6, 4, 7),
                PartPose.ZERO);

        PartDefinition rightLeg = root.getChild("right_leg");
        rightLeg.addOrReplaceChild("toe_cap",
                CubeListBuilder.create().texOffs(92, 10).addBox(-2.5F, 10.0F, -3.75F, 5, 3, 1),
                PartPose.ZERO);
        rightLeg.addOrReplaceChild("heel_fin",
                CubeListBuilder.create().texOffs(80, 10).addBox(-3.75F, 8.5F, 0.0F, 1, 3, 4),
                PartPose.ZERO);
        PartDefinition leftLeg = root.getChild("left_leg");
        leftLeg.addOrReplaceChild("toe_cap",
                CubeListBuilder.create().texOffs(92, 10).mirror().addBox(-2.5F, 10.0F, -3.75F, 5, 3, 1),
                PartPose.ZERO);
        leftLeg.addOrReplaceChild("heel_fin",
                CubeListBuilder.create().texOffs(80, 10).mirror().addBox(2.75F, 8.5F, 0.0F, 1, 3, 4),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    /** Leggings; same inflation as vanilla inner armor. */
    public static LayerDefinition createInnerLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.5F), 0.0F);
        PartDefinition root = mesh.getRoot();

        root.getChild("body").addOrReplaceChild("belt_buckle",
                CubeListBuilder.create().texOffs(76, 0).addBox(-1.5F, 9.0F, -3.0F, 3, 2, 1),
                PartPose.ZERO);

        PartDefinition rightLeg = root.getChild("right_leg");
        rightLeg.addOrReplaceChild("knee_plate",
                CubeListBuilder.create().texOffs(64, 0).addBox(-2.0F, 4.0F, -3.25F, 4, 3, 1),
                PartPose.ZERO);
        rightLeg.addOrReplaceChild("tasset",
                CubeListBuilder.create().texOffs(64, 8).addBox(-3.25F, -0.5F, -2.0F, 1, 5, 4),
                PartPose.ZERO);
        PartDefinition leftLeg = root.getChild("left_leg");
        leftLeg.addOrReplaceChild("knee_plate",
                CubeListBuilder.create().texOffs(64, 0).mirror().addBox(-2.0F, 4.0F, -3.25F, 4, 3, 1),
                PartPose.ZERO);
        leftLeg.addOrReplaceChild("tasset",
                CubeListBuilder.create().texOffs(64, 8).mirror().addBox(2.25F, -0.5F, -2.0F, 1, 5, 4),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    /** Rebakes on every renderer reload so resource-pack layer overrides stay current. */
    public static void bake(EntityModelSet models) {
        outer = new HumanoidModel<>(models.bakeLayer(OUTER_LAYER));
        inner = new HumanoidModel<>(models.bakeLayer(INNER_LAYER));
    }

    @Nullable
    public static HumanoidModel<LivingEntity> forSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD, CHEST, FEET -> outer;
            case LEGS -> inner;
            default -> null;
        };
    }
}
