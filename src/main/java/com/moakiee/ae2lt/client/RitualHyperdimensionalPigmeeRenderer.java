package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.entity.RitualHyperdimensionalPigmeeEntity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;

public final class RitualHyperdimensionalPigmeeRenderer extends ItemEntityRenderer {
    public RitualHyperdimensionalPigmeeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ItemEntityRenderState createRenderState() {
        return new RitualState();
    }

    @Override
    public void extractRenderState(ItemEntity entity, ItemEntityRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        ((RitualState) state).ceremonyScale = entity instanceof RitualHyperdimensionalPigmeeEntity ritualEntity
                ? ritualEntity.getCeremonyScale(partialTick) : 1.0F;
    }

    @Override
    public void submit(ItemEntityRenderState state, PoseStack poseStack,
            SubmitNodeCollector collector, CameraRenderState cameraState) {
        poseStack.pushPose();
        float scale = ((RitualState) state).ceremonyScale;
        poseStack.scale(scale, scale, scale);
        super.submit(state, poseStack, collector, cameraState);
        poseStack.popPose();
    }

    private static final class RitualState extends ItemEntityRenderState {
        private float ceremonyScale = 1.0F;
    }
}
