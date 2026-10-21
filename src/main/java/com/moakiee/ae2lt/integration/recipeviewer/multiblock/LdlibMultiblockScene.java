package com.moakiee.ae2lt.integration.recipeviewer.multiblock;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.joml.Vector3f;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.data.BlockPosFace;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

/**
 * Orthographic multiblock viewport backed by LDLib2.
 *
 * <p>The recipe-viewer chrome (toolbar, materials, details) stays in
 * {@link InteractiveMultiblockPreview}. LDLib owns the dummy world, camera,
 * block picking, and selection overlay. JEI and EMI do not deliver events to
 * an LDLib {@code UIElement}, so camera changes call the Scene camera API directly.</p>
 */
final class LdlibMultiblockScene {
    private static final float DEFAULT_YAW = 225.0F;
    private static final float DEFAULT_PITCH = 30.0F;

    private final Map<BlockPos, MultiblockStructureRecipe.Cell> cells = new HashMap<>();
    private final Scene scene = new Scene();
    private final Vector3f framedCenter = new Vector3f();

    private float framedZoom = 8.0F;
    private int visibleKey = Integer.MIN_VALUE;

    LdlibMultiblockScene(MultiblockStructureRecipe recipe) {
        TrackedDummyWorld world = new TrackedDummyWorld();
        for (var cell : recipe.cells()) {
            if (cell.state().isAir()) {
                continue;
            }
            BlockPos pos = cell.localPos();
            cells.put(pos, cell);
            try {
                world.addBlock(pos, BlockInfo.fromBlockState(cell.state()));
            } catch (RuntimeException ignored) {
                world.addBlock(pos, new BlockInfo(cell.state()));
            }
        }

        scene.createScene(world);
        scene.useOrtho(true);
        scene.setRenderFacing(true);
        scene.setRenderSelect(false);
        scene.setIntractable(true);
        scene.setDraggable(true);
        scene.setScalable(true);
        scene.setCameraYawAndPitch(DEFAULT_YAW, DEFAULT_PITCH);
        scene.getRenderer().setOnLookingAt(ignored -> {
            // The renderer only raycasts when this callback is set.
        });
        if (!cells.isEmpty()) {
            scene.setRenderedCore(cells.keySet(), null, true);
        }
        float framed = scene.getZoom();
        if (Float.isFinite(framed) && framed >= 0.1F) {
            framedZoom = framed;
        }
        Vector3f center = scene.getCenter();
        if (center != null) {
            framedCenter.set(center);
        } else {
            framedCenter.set(recipe.sizeX() * 0.5F, recipe.sizeY() * 0.5F, recipe.sizeZ() * 0.5F);
        }
    }

    void render(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int height,
            double mouseX,
            double mouseY,
            int visibleKey,
            Collection<BlockPos> visiblePositions,
            float yaw,
            float pitch) {
        var renderer = scene.getRenderer();
        if (renderer == null) {
            return;
        }
        if (visibleKey != this.visibleKey) {
            this.visibleKey = visibleKey;
            Set<BlockPos> hidden = new HashSet<>(cells.keySet());
            hidden.removeAll(visiblePositions);
            renderer.setBlocked(hidden);
        }
        scene.setCameraYawAndPitch(yaw, pitch);
        guiGraphics.flush();
        renderer.render(guiGraphics.pose(), x, y, width, height, (int) mouseX, (int) mouseY);
        scene.renderBlockOverLay(renderer);
    }

    void drag(int button, double dragX, double dragY) {
        if (button == 2) {
            pan((float) dragX, (float) dragY);
        } else if (button == 0) {
            scene.setCameraYawAndPitch(
                    scene.getRotationYaw() + (float) dragX,
                    net.minecraft.util.Mth.clamp(scene.getRotationPitch() + (float) dragY, -89.9F, 89.9F));
        }
    }

    void scroll(double scrollY) {
        scene.setZoom(net.minecraft.util.Mth.clamp(
                scene.getZoom() + (float) (scrollY < 0 ? 0.5F : -0.5F),
                0.1F,
                999.0F));
    }

    private void pan(float dragX, float dragY) {
        float yaw = scene.getRotationYaw();
        float pitch = scene.getRotationPitch();
        Vector3f forward = new Vector3f(
                (float) (Math.cos(Math.toRadians(pitch)) * Math.cos(Math.toRadians(yaw))),
                (float) Math.sin(Math.toRadians(pitch)),
                (float) (Math.cos(Math.toRadians(pitch)) * Math.sin(Math.toRadians(yaw))));
        Vector3f right = new Vector3f();
        forward.cross(new Vector3f(0.0F, 1.0F, 0.0F), right);
        if (right.lengthSquared() < 1.0E-4F) {
            right.set(1.0F, 0.0F, 0.0F);
        }
        right.normalize();
        Vector3f up = new Vector3f();
        right.cross(forward, up).normalize();
        float move = scene.getZoom() * 0.005F;
        Vector3f center = new Vector3f(scene.getCenter());
        center.add(
                right.x * dragX * move + up.x * dragY * move,
                right.y * dragX * move + up.y * dragY * move,
                right.z * dragX * move + up.z * dragY * move);
        scene.setCenter(center);
    }

    void resetCamera() {
        scene.setCenter(new Vector3f(framedCenter));
        scene.setZoom(framedZoom);
        scene.setCameraYawAndPitch(DEFAULT_YAW, DEFAULT_PITCH);
    }

    float yaw() {
        return scene.getRotationYaw();
    }

    float pitch() {
        return scene.getRotationPitch();
    }

    void setYaw(float yaw) {
        scene.setCameraYawAndPitch(yaw, scene.getRotationPitch());
    }

    MultiblockStructureRecipe.Cell pick() {
        return cellAt(scene.getLastHoverPosFace());
    }

    private MultiblockStructureRecipe.Cell cellAt(BlockPosFace face) {
        return face == null ? null : cells.get(face.pos());
    }

}
