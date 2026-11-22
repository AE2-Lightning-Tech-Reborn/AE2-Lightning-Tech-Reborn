package com.moakiee.ae2lt.integration.recipeviewer.multiblock;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.moakiee.ae2lt.lightning.strike.LightningStrikeRecipe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Shared native block rendering for the recipe viewers' non-interactive strike previews. */
public final class RotatingStructurePreview {
    private static final float X_ROTATION_DEG = 30.0F;
    private static final float SCALE_MARGIN = 0.92F;

    private final List<Entry> blocks;
    private final int width;
    private final int height;
    private final float centerX;
    private final float centerY;
    private final float centerZ;
    private final float scale;

    private RotatingStructurePreview(int width, int height, List<Entry> blocks) {
        this.width = width;
        this.height = height;
        this.blocks = blocks;
        if (blocks.isEmpty()) {
            centerX = 0;
            centerY = 0;
            centerZ = 0;
            scale = 0;
            return;
        }

        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (Entry entry : blocks) {
            minX = Math.min(minX, entry.offset.getX() - 0.5F);
            maxX = Math.max(maxX, entry.offset.getX() + 0.5F);
            minY = Math.min(minY, entry.offset.getY() - 0.5F);
            maxY = Math.max(maxY, entry.offset.getY() + 0.5F);
            minZ = Math.min(minZ, entry.offset.getZ() - 0.5F);
            maxZ = Math.max(maxZ, entry.offset.getZ() + 0.5F);
        }
        centerX = (minX + maxX) * 0.5F;
        centerY = (minY + maxY) * 0.5F;
        centerZ = (minZ + maxZ) * 0.5F;

        float xRotCos = (float) Math.cos(Math.toRadians(X_ROTATION_DEG));
        float xRotSin = (float) Math.sin(Math.toRadians(X_ROTATION_DEG));
        float horizontalRadius = 0F;
        float verticalRadius = 0F;
        float[] xs = {minX - centerX, maxX - centerX};
        float[] ys = {minY - centerY, maxY - centerY};
        float[] zs = {minZ - centerZ, maxZ - centerZ};
        for (float xv : xs) {
            for (float yv : ys) {
                for (float zv : zs) {
                    float horizontal = (float) Math.hypot(xv, zv);
                    horizontalRadius = Math.max(horizontalRadius, horizontal);
                    verticalRadius = Math.max(verticalRadius,
                            Math.abs(yv) * xRotCos + horizontal * xRotSin);
                }
            }
        }
        if (horizontalRadius <= 0F || verticalRadius <= 0F) {
            scale = 0F;
            return;
        }
        float widthScale = width * 0.5F / horizontalRadius;
        float heightScale = height * 0.5F / verticalRadius;
        scale = Math.min(widthScale, heightScale) * SCALE_MARGIN;
    }

    public static RotatingStructurePreview lightningStrike(LightningStrikeRecipe recipe, int width, int height) {
        var blocks = new ArrayList<Entry>();
        for (var requirement : recipe.requirements()) {
            addBlock(blocks, requirement.block(), requirement.offset());
        }
        addBlock(blocks, recipe.centerInput(), BlockPos.ZERO);
        addBlock(blocks, Blocks.LIGHTNING_ROD, new BlockPos(0, 1, 0));
        return new RotatingStructurePreview(width, height, List.copyOf(blocks));
    }

    private static void addBlock(List<Entry> blocks, Block block, BlockPos offset) {
        if (block != null) {
            blocks.add(new Entry(block.defaultBlockState(), offset));
        }
    }

    /** JEI passes its already translated origin; EMI supplies the widget's bounds origin. */
    public void draw(GuiGraphics graphics, int x, int y, float rotation) {
        if (blocks.isEmpty() || scale <= 0F) {
            return;
        }
        var client = Minecraft.getInstance();
        MultiBufferSource bufferSource = client.renderBuffers().bufferSource();
        var blockRenderer = client.getBlockRenderer();
        var pose = graphics.pose();
        for (Entry entry : blocks) {
            pose.pushPose();
            pose.translate(x + width / 2F, y + height / 2F, 400);
            pose.scale(scale, -scale, scale);
            pose.mulPose(Axis.XP.rotationDegrees(X_ROTATION_DEG));
            pose.mulPose(Axis.YP.rotationDegrees(225 + rotation));
            pose.translate(
                    -0.5F + entry.offset.getX() - centerX,
                    -0.5F + entry.offset.getY() - centerY,
                    -0.5F + entry.offset.getZ() - centerZ);
            RenderSystem.runAsFancy(() -> {
                if (entry.state.getRenderShape() != RenderShape.ENTITYBLOCK_ANIMATED) {
                    blockRenderer.renderSingleBlock(entry.state, pose, bufferSource,
                            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                }
            });
            pose.popPose();
        }
        if (bufferSource instanceof MultiBufferSource.BufferSource source) {
            source.endBatch();
        }
        Lighting.setupFor3DItems();
    }

    private record Entry(BlockState state, BlockPos offset) {
    }
}
