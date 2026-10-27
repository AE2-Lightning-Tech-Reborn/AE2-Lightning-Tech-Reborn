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
    private final BarrelInventoryNotificationBatch root;
    // Most scopes touch one barrel. Allocate collections only for additional targets.
    private Level firstLevel;
    private BlockPos firstPos;
    private Map<Level, Map<BlockPos, Block>> pending;
    private Level firstDirtyLevel;
    private BlockPos firstDirtyPos;
    private Map<Level, Set<BlockPos>> dirty;
    private Level lastLevel;
    private BlockPos lastPos;
    private Level lastDirtyLevel;
    private BlockPos lastDirtyPos;

    private BarrelInventoryNotificationBatch() {
        parent = CURRENT.get();
        root = parent == null ? this : parent.root;
        CURRENT.set(this);
    }

    public static BarrelInventoryNotificationBatch open() {
        return new BarrelInventoryNotificationBatch();
    }

    public static boolean defer(Level level, BlockPos pos, Block block) {
        var batch = CURRENT.get();
        if (batch == null || level.isClientSide || block != Blocks.BARREL) return false;
        batch = batch.root;
        if (batch.lastLevel == level && pos.equals(batch.lastPos)) return true;
        var immutablePos = pos.immutable();
        if (batch.firstPos == null) {
            batch.firstLevel = level;
            batch.firstPos = immutablePos;
        } else if (batch.firstLevel != level || !pos.equals(batch.firstPos)) {
            if (batch.pending == null) batch.pending = new IdentityHashMap<>();
            batch.pending.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(immutablePos, block);
        }
        batch.lastLevel = level;
        batch.lastPos = immutablePos;
        return true;
    }

    /** The first write still marks the chunk synchronously. */
    public static boolean alreadyMarkedDirty(Level level, BlockPos pos, Block block) {
        var batch = CURRENT.get();
        if (batch == null || level.isClientSide || block != Blocks.BARREL) return false;
        batch = batch.root;
        if (batch.lastDirtyLevel == level && pos.equals(batch.lastDirtyPos)) return true;
        var immutablePos = pos.immutable();
        boolean repeated;
        if (batch.firstDirtyPos == null) {
            batch.firstDirtyLevel = level;
            batch.firstDirtyPos = immutablePos;
            repeated = false;
        } else if (batch.firstDirtyLevel == level && pos.equals(batch.firstDirtyPos)) {
            repeated = true;
        } else {
            if (batch.dirty == null) batch.dirty = new IdentityHashMap<>();
            repeated = !batch.dirty.computeIfAbsent(level, ignored -> new HashSet<>()).add(immutablePos);
        }
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
        if (firstPos != null) failure = notify(firstLevel, firstPos, Blocks.BARREL, failure);
        if (pending != null) {
            for (var level : pending.entrySet()) {
                for (var update : level.getValue().entrySet()) {
                    failure = notify(level.getKey(), update.getKey(), update.getValue(), failure);
                }
            }
        }
        if (failure != null) throw failure;
    }

    private static RuntimeException notify(Level level, BlockPos pos, Block block, RuntimeException failure) {
        try {
            level.updateNeighbourForOutputSignal(pos, block);
        } catch (RuntimeException e) {
            if (failure == null) return e;
            if (failure != e) failure.addSuppressed(e);
        }
        return failure;
    }
}
