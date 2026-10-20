package com.moakiee.ae2lt.integration.emi;

import java.util.ArrayList;
import java.util.List;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

/** EMI counterpart of the JEI lightning-strike preview, rendered by LDLib2. */
final class EmiLightningStrikePreviewWidget extends Widget {
    private static final float ROTATION_DEGREES_PER_MS = 0.024F;

    private final Bounds bounds;
    private final Scene scene = new Scene();
    private final float baseYaw;

    private EmiLightningStrikePreviewWidget(int x, int y, int width, int height, List<Entry> blocks) {
        this.bounds = new Bounds(x, y, width, height);

        TrackedDummyWorld world = new TrackedDummyWorld();
        List<BlockPos> positions = new ArrayList<>();
        for (Entry entry : blocks) {
            positions.add(entry.offset);
            try {
                world.addBlock(entry.offset, BlockInfo.fromBlockState(entry.block.defaultBlockState()));
            } catch (RuntimeException ignored) {
                world.addBlock(entry.offset, new BlockInfo(entry.block.defaultBlockState()));
            }
        }
        scene.createScene(world);
        scene.useOrtho(true);
        scene.setRenderFacing(false);
        scene.setRenderSelect(false);
        scene.setIntractable(false);
        scene.setCameraYawAndPitch(225.0F, 30.0F);
        if (!positions.isEmpty()) {
            scene.setRenderedCore(positions, null, true);
            scene.setZoom(scene.getZoom() * 0.62F);
            scene.setCenter(new org.joml.Vector3f(scene.getCenter()).add(0.0F, -2.05F, 0.0F));
        }
        baseYaw = scene.getRotationYaw();
        scene.getRenderer().setOnLookingAt(ignored -> {
            // Required for the renderer to update its trace result.
        });
    }

    @Override
    public Bounds getBounds() {
        return bounds;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        var renderer = scene.getRenderer();
        if (renderer == null) {
            return;
        }
        scene.setCameraYawAndPitch(
                baseYaw + (Util.getMillis() * ROTATION_DEGREES_PER_MS) % 360.0F,
                scene.getRotationPitch());
        graphics.flush();
        renderer.render(graphics.pose(), bounds.x(), bounds.y(), bounds.width(), bounds.height(), mouseX, mouseY);
    }

    private record Entry(Block block, BlockPos offset) {
    }

    static final class Builder {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final List<Entry> blocks = new ArrayList<>();

        Builder(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        Builder addBlock(Block block, BlockPos offset) {
            if (block != null && offset != null) {
                blocks.add(new Entry(block, offset));
            }
            return this;
        }

        EmiLightningStrikePreviewWidget build() {
            return new EmiLightningStrikePreviewWidget(x, y, width, height, List.copyOf(blocks));
        }
    }
}
