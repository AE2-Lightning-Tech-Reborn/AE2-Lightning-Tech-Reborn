package com.moakiee.ae2lt.api.patternprovider;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Immutable public snapshot of a configured wireless destination. */
public record WirelessEndpoint(ResourceKey<Level> dimension, BlockPos pos, Direction boundFace) {
    public WirelessEndpoint {
        Objects.requireNonNull(dimension);
        pos = Objects.requireNonNull(pos).immutable();
        Objects.requireNonNull(boundFace);
    }
}
