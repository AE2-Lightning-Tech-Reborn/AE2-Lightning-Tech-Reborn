package com.moakiee.ae2lt.client.render;

import java.util.ArrayList;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/** Smooth per-pixel rainbow bands on the portal silhouette, through the native submission API. */
final class RainbowPigmeeSurfaceLayer {
    private static final float SURFACE_OFFSET = 0.002F;
    private static final RenderType SURFACE = RenderType.create("ae2lt_rainbow_pigmee",
            RenderSetup.builder(RainbowPigmeeShader.PIPELINE).bufferSize(4096).createRenderSetup());
    private RainbowPigmeeSurfaceLayer() {}
    static void submit(BlockStateModel model, PoseStack poses, SubmitNodeCollector collector, Direction facing) {
        var quads = new ArrayList<BakedQuad>();
        var parts = new ArrayList<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>();
        model.collectParts(RandomSource.create(42), parts);
        for (var part : parts) {
            for (var side : Direction.values()) quads.addAll(part.getQuads(side));
            quads.addAll(part.getQuads(null));
        }
        collector.submitCustomGeometry(poses, SURFACE, (pose, consumer) -> {
            for (var quad : quads) {
                var side = quad.direction();
                float shade = switch (side) {
                    case UP -> 1.0F;
                    case DOWN -> 0.50F;
                    case NORTH, SOUTH -> 0.80F;
                    case EAST, WEST -> 0.60F;
                };
                for (int vertex = 0; vertex < 4; vertex++) {
                    var p = quad.position(vertex);
                    float u = side.getAxis() == Direction.Axis.X ? p.z() : p.x();
                    float v = side.getAxis() == Direction.Axis.Y ? p.z() : p.y();
                    float depth = switch (facing) {
                        case SOUTH -> 1 - p.z();
                        case EAST -> 1 - p.x();
                        case WEST -> p.x();
                        default -> p.z();
                    };
                    consumer.addVertex(pose.pose(), p.x() + side.getStepX() * SURFACE_OFFSET,
                            p.y() + side.getStepY() * SURFACE_OFFSET, p.z() + side.getStepZ() * SURFACE_OFFSET)
                            .setUv(u, v).setColor(shade, shade, shade, 1)
                            .setUv2(Math.round(depth * 4096), Math.round(p.y() * 4096));
                }
            }
        });
    }
}
