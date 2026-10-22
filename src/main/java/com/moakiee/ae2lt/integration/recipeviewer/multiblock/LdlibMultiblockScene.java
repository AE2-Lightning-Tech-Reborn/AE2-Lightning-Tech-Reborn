package com.moakiee.ae2lt.integration.recipeviewer.multiblock;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.joml.Vector3f;

import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.BlockPosFace;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

/**
 * Orthographic multiblock viewport backed by LDLib.
 *
 * <p>The recipe-viewer chrome (toolbar, materials, details) stays in
 * {@link InteractiveMultiblockPreview}. LDLib owns the dummy world, camera,
 * block picking, and selection overlay. JEI and EMI do not deliver events to
 * an LDLib {@code Widget}, so camera changes call the SceneWidget camera API directly.</p>
 */
final class LdlibMultiblockScene {
    private static final float DEFAULT_YAW = 225.0F;
    private static final float DEFAULT_PITCH = 30.0F;

    private final Map<BlockPos, MultiblockStructureRecipe.Cell> cells = new HashMap<>();
    private final PreviewScene scene = new PreviewScene();
    private final Vector3f framedCenter = new Vector3f();

    private float framedZoom = 8.0F;
    private int visibleKey = Integer.MIN_VALUE;

    LdlibMultiblockScene(MultiblockStructureRecipe recipe, int width, int height) {
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
        setCamera(DEFAULT_YAW, DEFAULT_PITCH);
        scene.getRenderer().setOnLookingAt(scene::hover);
        LdlibSceneFraming.frame(scene, cells.keySet(), width, height);
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
            Set<BlockPos> visible = new HashSet<>(visiblePositions);
            Set<BlockPos> hidden = new HashSet<>(cells.keySet());
            hidden.removeAll(visible);
            // LDLib 1's blocked set only skips drawing. Picking and face culling read the world.
            scene.getDummyWorld().setRenderFilter(visible::contains);
            renderer.setBlocked(hidden);
            scene.hover(null);
        }
        setCamera(yaw, pitch);
        guiGraphics.flush();
        renderer.render(guiGraphics.pose(), x, y, width, height, (int) mouseX, (int) mouseY);
    }

    void drag(int button, double dragX, double dragY) {
        if (button == 2) {
            pan((float) dragX, (float) dragY);
        } else if (button == 0) {
            setCamera(
                    scene.getRotationPitch() + (float) dragX,
                    net.minecraft.util.Mth.clamp(scene.getRotationYaw() + (float) dragY, -89.9F, 89.9F));
        }
    }

    void scroll(double scrollY) {
        scene.setZoom(net.minecraft.util.Mth.clamp(
                scene.getZoom() + (float) (scrollY < 0 ? 0.5F : -0.5F),
                0.1F,
                999.0F));
    }

    private void pan(float dragX, float dragY) {
        float yaw = scene.getRotationPitch();
        float pitch = scene.getRotationYaw();
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
        setCamera(DEFAULT_YAW, DEFAULT_PITCH);
    }

    float yaw() {
        return scene.getRotationPitch();
    }

    float pitch() {
        return scene.getRotationYaw();
    }

    void setYaw(float yaw) {
        setCamera(yaw, scene.getRotationYaw());
    }

    MultiblockStructureRecipe.Cell pick() {
        return cellAt(scene.getHoverPosFace());
    }

    // LDLib 1 stores elevation in rotationYaw and azimuth in rotationPitch.
    private void setCamera(float yaw, float pitch) {
        scene.setCameraYawAndPitch(pitch, yaw);
    }

    private static final class PreviewScene extends SceneWidget {
        private PreviewScene() {
            super(0, 0, 1, 1, null);
        }

        private void hover(net.minecraft.world.phys.BlockHitResult hit) {
            hoverPosFace = hit == null ? null : new BlockPosFace(hit.getBlockPos(), hit.getDirection());
        }
    }

    private MultiblockStructureRecipe.Cell cellAt(BlockPosFace face) {
        return face == null ? null : cells.get(face.pos);
    }

}
