package com.moakiee.ae2lt.integration.recipeviewer.multiblock;

import java.util.Collection;

import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;

import net.minecraft.core.BlockPos;

/** Fits an LDLib 1 orthographic camera to a recipe viewport at every rotation. */
public final class LdlibSceneFraming {
    private LdlibSceneFraming() {
    }

    public static void frame(SceneWidget scene, Collection<BlockPos> positions, int width, int height) {
        // LDLib 1 defaults to zero and its automatic zoom assumes a perspective camera.
        scene.setOrthoRange(0.5F);
        if (positions.isEmpty()) {
            return;
        }
        scene.setRenderedCore(positions);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        double sizeX = (double) maxX - minX + 1;
        double sizeY = (double) maxY - minY + 1;
        double sizeZ = (double) maxZ - minZ + 1;
        double diameter = Math.sqrt(sizeX * sizeX + sizeY * sizeY + sizeZ * sizeZ);
        double aspect = (double) Math.max(1, width) / Math.max(1, height);
        // LDLib fixes the horizontal range and divides the vertical range by the aspect ratio.
        scene.setZoom((float) (diameter * Math.max(1, aspect) * 1.05));
    }
}
