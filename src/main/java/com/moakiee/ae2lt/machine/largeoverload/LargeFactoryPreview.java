package com.moakiee.ae2lt.machine.largeoverload;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3f;

/** Uses the scanner's exact cell table and rotation. Preview is read-only and bounded. */
public final class LargeFactoryPreview {
    private LargeFactoryPreview() { }
    public static void show(LargeFactoryControllerBlockEntity controller) {
        if (!(controller.getLevel() instanceof ServerLevel level)) return;
        var cells = LargeFactoryStructure.cells();
        int start = Math.floorMod((int) (level.getGameTime() / 20) * 64, cells.size());
        int emitted = 0;
        for (int i = 0; i < cells.size() && emitted < 64; i++) {
            var cell = cells.get((start + i) % cells.size());
            var pos = LargeFactoryStructure.worldPosition(controller.getBlockPos(), cell.localPosition(), controller.facing());
            if (!level.isLoaded(pos)) continue;
            var actual = LargeFactoryRegistration.component(level.getBlockState(pos));
            if (cell.role() == LargeFactoryStructure.Role.AIR && actual == LargeFactoryComponent.AIR) continue;
            Vector3f color = switch (cell.role()) {
                case FRAME -> new Vector3f(0.2f, 0.7f, 1);
                case CASING -> new Vector3f(0.4f, 0.7f, 0.8f);
                case CORE -> new Vector3f(0.85f, 0.4f, 1);
                case HATCH -> new Vector3f(1, 0.75f, 0.15f);
                case AIR -> new Vector3f(1, 0.1f, 0.1f);
                default -> new Vector3f(0.2f, 1, 0.7f);
            };
            level.sendParticles(new DustParticleOptions(color, 1), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    2, 0.25, 0.25, 0.25, 0);
            emitted++;
        }
    }
}
