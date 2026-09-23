package com.moakiee.ae2lt.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/** Replays the Fumo silhouette into the End Portal shader. */
final class HyperdimensionalPigmeePortalLayer {
    private static final float SURFACE_OFFSET = 0.002F;

    private HyperdimensionalPigmeePortalLayer() {}

    static void submit(BlockStateModel model, PoseStack poseStack, SubmitNodeCollector collector) {
        var parts = new ArrayList<BlockStateModelPart>();
        model.collectParts(RandomSource.create(42L), parts);
        var quads = new ArrayList<BakedQuad>();
        for (var part : parts) {
            for (Direction direction : Direction.values()) quads.addAll(part.getQuads(direction));
            quads.addAll(part.getQuads(null));
        }
        if (quads.isEmpty()) return;
        List<BakedQuad> snapshot = List.copyOf(quads);
        collector.submitCustomGeometry(poseStack, RenderTypes.endPortal(), (pose, consumer) -> {
            for (BakedQuad quad : snapshot) {
                Direction direction = quad.direction();
                float dx = direction == null ? 0 : direction.getStepX() * SURFACE_OFFSET;
                float dy = direction == null ? 0 : direction.getStepY() * SURFACE_OFFSET;
                float dz = direction == null ? 0 : direction.getStepZ() * SURFACE_OFFSET;
                for (int i = 0; i < 4; i++) {
                    var vertex = quad.position(i);
                    consumer.addVertex(pose.pose(), vertex.x() + dx, vertex.y() + dy, vertex.z() + dz);
                }
            }
        });
    }
}
