package com.moakiee.ae2lt.logic.persistence;

import com.moakiee.ae2lt.block.MatrixControllerBlock;
import com.moakiee.ae2lt.block.MatrixMultiblockComponentBlock;
import com.moakiee.ae2lt.block.MultiblockStateProperties;
import com.moakiee.ae2lt.block.TianshuSupercomputerPortBlock;
import com.moakiee.ae2lt.block.TianshuSupercomputerStructureBlock;
import com.moakiee.ae2lt.block.TianshuSupercomputingUnitBlock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-dimension visual cleanup for members skipped while their chunk was unloaded. */
public final class MultiblockRenderCleanupSavedData extends SavedData {
    private static final String DATA_NAME = "ae2lt_multiblock_render_cleanup";
    private final Map<Long, Set<Long>> pending = new HashMap<>();
    private final Set<Long> readyChunks = new HashSet<>();

    public static MultiblockRenderCleanupSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                MultiblockRenderCleanupSavedData::load, MultiblockRenderCleanupSavedData::new, DATA_NAME);
    }

    public static MultiblockRenderCleanupSavedData getIfPresent(ServerLevel level) {
        return level.getDataStorage().get(MultiblockRenderCleanupSavedData::load, DATA_NAME);
    }

    public void defer(BlockPos pos) {
        long chunk = new ChunkPos(pos).toLong();
        if (pending.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(pos.asLong())) setDirty();
        // The same removal may later load this chunk through vanilla neighbour updates.
        readyChunks.add(chunk);
    }

    public void cancel(BlockPos pos) {
        long chunk = new ChunkPos(pos).toLong();
        var positions = pending.get(chunk);
        if (positions != null && positions.remove(pos.asLong())) {
            if (positions.isEmpty()) {
                pending.remove(chunk);
                readyChunks.remove(chunk);
            }
            setDirty();
        }
    }

    public void chunkLoaded(ChunkPos pos) {
        if (pending.containsKey(pos.toLong())) readyChunks.add(pos.toLong());
    }

    public void applyLoadedChunks(ServerLevel level) {
        var ready = Set.copyOf(readyChunks);
        readyChunks.clear();
        for (long key : ready) {
            var chunk = new ChunkPos(key);
            if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) continue;
            var positions = pending.get(key);
            if (positions == null) continue;
            for (long packed : Set.copyOf(positions)) {
                var pos = BlockPos.of(packed);
                var state = level.getBlockState(pos);
                if (isMember(state) && state.getValue(MultiblockStateProperties.FORMED)) {
                    // Only the visual property changes; avoid loading neighbouring chunks for shape updates.
                    level.setBlock(pos, state.setValue(MultiblockStateProperties.FORMED, false),
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                cancel(pos);
            }
        }
    }

    private static boolean isMember(BlockState state) {
        if (!state.hasProperty(MultiblockStateProperties.FORMED)
                || state.getBlock() instanceof MatrixControllerBlock) return false;
        return state.getBlock() instanceof MatrixMultiblockComponentBlock
                || state.getBlock() instanceof TianshuSupercomputerStructureBlock
                || state.getBlock() instanceof TianshuSupercomputingUnitBlock
                || state.getBlock() instanceof TianshuSupercomputerPortBlock;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("Positions", pending.values().stream().flatMap(Set::stream)
                .mapToLong(Long::longValue).sorted().toArray());
        return tag;
    }

    public static MultiblockRenderCleanupSavedData load(CompoundTag tag) {
        var data = new MultiblockRenderCleanupSavedData();
        for (long packed : tag.getLongArray("Positions")) data.defer(BlockPos.of(packed));
        data.setDirty(false);
        return data;
    }
}
