package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/** Full-bright markings layered above the End Portal silhouette. */
final class HyperdimensionalPigmeeTextureLayer {
    static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(
            AE2LightningTech.MODID, "block/hyperdimensional_pigmee_fumo_overlay");
    static final StandaloneModelKey<QuadCollection> MODEL = new StandaloneModelKey<>(MODEL_ID::toString);
    private static final float SURFACE_OFFSET = 0.004F;

    private HyperdimensionalPigmeeTextureLayer() {}

    static void submitBlock(BlockState state, PoseStack poseStack, SubmitNodeCollector collector, int overlay) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationFor(state)));
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        submit(poseStack, collector, overlay);
        poseStack.popPose();
    }

    static void submitItem(PoseStack poseStack, SubmitNodeCollector collector, int overlay) {
        submit(poseStack, collector, overlay);
    }

    private static void submit(PoseStack poseStack, SubmitNodeCollector collector, int overlay) {
        QuadCollection model = Minecraft.getInstance().getModelManager().getStandaloneModel(MODEL);
        if (model == null) return;
        var quads = model.getAll();
        collector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS),
                (pose, consumer) -> {
                    var quadInstance = new QuadInstance();
                    quadInstance.setColor(-1);
                    quadInstance.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
                    quadInstance.setOverlayCoords(overlay);
                    for (var quad : quads) {
                        var direction = quad.direction();
                        float dx = direction == null ? 0 : direction.getStepX() * SURFACE_OFFSET;
                        float dy = direction == null ? 0 : direction.getStepY() * SURFACE_OFFSET;
                        float dz = direction == null ? 0 : direction.getStepZ() * SURFACE_OFFSET;
                        var offsetPose = new PoseStack();
                        offsetPose.last().pose().set(pose.pose()).translate(dx, dy, dz);
                        consumer.putBakedQuad(offsetPose.last(), quad, quadInstance);
                    }
                });
    }

    private static float rotationFor(BlockState state) {
        if (!state.hasProperty(com.moakiee.ae2lt.block.FumoBlock.FACING)) return 0.0F;
        Direction facing = state.getValue(com.moakiee.ae2lt.block.FumoBlock.FACING);
        return switch (facing) {
            case SOUTH -> -180.0F;
            case WEST -> -270.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
    }
}
