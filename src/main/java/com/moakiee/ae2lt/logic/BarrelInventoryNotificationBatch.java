package com.moakiee.ae2lt.logic;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Marks each barrel dirty once and coalesces neighbor notifications within one interface transfer. */
public final class BarrelInventoryNotificationBatch implements AutoCloseable {
    private static final ThreadLocal<BarrelInventoryNotificationBatch> CURRENT = new ThreadLocal<>();
    private final BarrelInventoryNotificationBatch parent;
    private final Map<Level, Map<BlockPos, Block>> pending;
    private final Map<Level, Set<BlockPos>> dirty;
    private Level lastLevel;
    private BlockPos lastPos;
    private Level lastDirtyLevel;
    private BlockPos lastDirtyPos;

    private BarrelInventoryNotificationBatch() {
        parent = CURRENT.get();
        pending = parent == null ? new IdentityHashMap<>() : parent.pending;
        dirty = parent == null ? new IdentityHashMap<>() : parent.dirty;
        CURRENT.set(this);
    }

    public static BarrelInventoryNotificationBatch open() {
        return new BarrelInventoryNotificationBatch();
    }

    public static boolean defer(Level level, BlockPos pos, Block block) {
        var batch = CURRENT.get();
        if (batch == null || level.isClientSide || block != Blocks.BARREL) return false;
        if (batch.lastLevel == level && pos.equals(batch.lastPos)) return true;
        var immutablePos = pos.immutable();
        batch.pending.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(immutablePos, block);
        batch.lastLevel = level;
        batch.lastPos = immutablePos;
        return true;
    }

    /** The first write still marks the chunk synchronously. */
    public static boolean alreadyMarkedDirty(Level level, BlockPos pos, Block block) {
        var batch = CURRENT.get();
        if (batch == null || level.isClientSide || block != Blocks.BARREL) return false;
        if (batch.lastDirtyLevel == level && pos.equals(batch.lastDirtyPos)) return true;
        var immutablePos = pos.immutable();
        boolean repeated = !batch.dirty.computeIfAbsent(level, ignored -> new HashSet<>()).add(immutablePos);
        batch.lastDirtyLevel = level;
        batch.lastDirtyPos = immutablePos;
        return repeated;
    }

    @Override
    public void close() {
        if (parent != null) {
            CURRENT.set(parent);
            return;
        }
        CURRENT.remove();
        RuntimeException failure = null;
        for (var level : pending.entrySet()) {
            for (var update : level.getValue().entrySet()) {
                try {
                    level.getKey().updateNeighbourForOutputSignal(update.getKey(), update.getValue());
                } catch (RuntimeException e) {
                    if (failure == null) failure = e;
                    else if (failure != e) failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) throw failure;
    }
}
