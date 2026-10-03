package com.moakiee.ae2lt.logic;

import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Shared server/client range check, measured from the triggering doll's center. */
public final class EasterEggAudience {
    public static final double RADIUS = 8.0;

    private EasterEggAudience() {}

    public static boolean includes(GlobalPos source, ResourceKey<Level> dimension, Vec3 position) {
        return source.dimension().equals(dimension)
                && position.distanceToSqr(Vec3.atCenterOf(source.pos())) <= RADIUS * RADIUS;
    }
}
