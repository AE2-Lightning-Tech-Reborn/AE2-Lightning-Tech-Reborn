package com.moakiee.ae2lt.client.render;

import com.moakiee.ae2lt.blockentity.FumoBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.ItemTransforms.TransformType;
import net.minecraftforge.client.model.BakedModelWrapper;

class SpinningFumoBakedModel extends BakedModelWrapper<BakedModel> {

    SpinningFumoBakedModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public BakedModel applyTransform(TransformType displayContext, PoseStack poseStack,
                                     boolean leftHand) {
        originalModel.applyTransform(displayContext, poseStack, leftHand);
        if (displayContext == TransformType.HEAD) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                float partialTick = minecraft.getFrameTime();
                float angle = (minecraft.level.getGameTime() % 60L + partialTick)
                        * FumoBlockEntity.SPIN_DEGREES_PER_TICK;
                poseStack.mulPose(Vector3f.YP.rotationDegrees(angle));
            }
        }
        return this;
    }
}
