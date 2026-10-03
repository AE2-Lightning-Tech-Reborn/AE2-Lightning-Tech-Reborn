package com.moakiee.ae2lt.logic.wireless.support;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class WirelessConnectorTargetHelper {
    private WirelessConnectorTargetHelper() {}

    public static Set<BlockPos> collectTargets(Level level, BlockPos origin, boolean contiguous) {
        return collectTargets(level, origin, contiguous, Integer.MAX_VALUE);
    }

    public static Set<BlockPos> collectTargets(
            Level level, BlockPos origin, boolean contiguous, int maxTargets) {
        return collectTargets(level, origin, contiguous, maxTargets, pos -> level.getBlockEntity(pos) != null);
    }

    public static Set<BlockPos> collectTargets(
            Level level, BlockPos origin, boolean contiguous, int maxTargets, Predicate<BlockPos> acceptsTarget) {
        if (maxTargets <= 0 || !level.isLoaded(origin) || !acceptsTarget.test(origin)) return Set.of();
        if (!contiguous) {
            return Set.of(origin.immutable());
        }
        var originState = level.getBlockState(origin);
        var originBlockEntity = level.getBlockEntity(origin);

        var visited = new LinkedHashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        queue.add(origin.immutable());
        while (!queue.isEmpty() && visited.size() < maxTargets) {
            var current = queue.removeFirst();
            if (!visited.add(current) || visited.size() >= maxTargets) continue;
            for (var direction : Direction.values()) {
                var next = current.relative(direction);
                if (visited.contains(next) || !level.isLoaded(next)) continue;
                var nextBlockEntity = level.getBlockEntity(next);
                if (originBlockEntity != null && (nextBlockEntity == null
                        || nextBlockEntity.getClass() != originBlockEntity.getClass())) continue;
                if (!level.getBlockState(next).is(originState.getBlock())) continue;
                if (!acceptsTarget.test(next)) continue;
                queue.addLast(next.immutable());
            }
        }
        return visited;
    }
}
