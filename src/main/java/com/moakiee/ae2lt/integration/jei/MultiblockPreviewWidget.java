package com.moakiee.ae2lt.integration.jei;

import java.util.ArrayList;
import java.util.List;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;

import mezz.jei.api.gui.widgets.IRecipeWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

/**
 * Small rotating multiblock preview for a JEI recipe layout.
 *
 * <p>The structure is rendered by an LDLib2 {@link Scene}. This widget only
 * advances the yaw; it does not add the interactive toolbar used by the full
 * structure preview.</p>
 */
public final class MultiblockPreviewWidget implements IRecipeWidget {
    private static final float ROTATION_PER_FRAME = 0.4F;

    private final ScreenPosition position;
    private final int width;
    private final int height;
    private final Scene scene = new Scene();

    private MultiblockPreviewWidget(int x, int y, int width, int height, List<Entry> blocks) {
        this.position = new ScreenPosition(x, y);
        this.width = width;
        this.height = height;

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
        scene.getRenderer().setOnLookingAt(ignored -> {
            // Required for the renderer to update its trace result.
        });
    }

    @Override
    public ScreenPosition getPosition() {
        return position;
    }

    @Override
    public void drawWidget(GuiGraphics guiGraphics, double mouseX, double mouseY) {
        var renderer = scene.getRenderer();
        if (renderer == null) {
            return;
        }
        guiGraphics.flush();
        renderer.render(
                guiGraphics.pose(),
                position.x(),
                position.y(),
                width,
                height,
                (int) mouseX,
                (int) mouseY);
    }

    @Override
    public void tick() {
        scene.setCameraYawAndPitch(scene.getRotationYaw() + ROTATION_PER_FRAME, scene.getRotationPitch());
    }

    public static Builder builder(int x, int y, int width, int height) {
        return new Builder(x, y, width, height);
    }

    private record Entry(Block block, BlockPos offset) {}

    public static final class Builder {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final List<Entry> blocks = new ArrayList<>();

        private Builder(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public Builder addBlock(Block block, BlockPos offset) {
            if (block != null && offset != null) {
                blocks.add(new Entry(block, offset));
            }
            return this;
        }

        public MultiblockPreviewWidget build() {
            return new MultiblockPreviewWidget(x, y, width, height, List.copyOf(blocks));
        }
    }
}
