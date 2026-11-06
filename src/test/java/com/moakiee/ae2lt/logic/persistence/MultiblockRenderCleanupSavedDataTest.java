package com.moakiee.ae2lt.logic.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class MultiblockRenderCleanupSavedDataTest {
    @Test
    void pendingCleanupSurvivesNbtRoundTripAcrossPositiveAndNegativeChunks() {
        var data = new MultiblockRenderCleanupSavedData();
        long[] expected = {new BlockPos(17, 70, -17).asLong(), new BlockPos(-17, 180, 40).asLong()};
        for (long pos : expected) data.defer(BlockPos.of(pos));
        Arrays.sort(expected);
        var restored = MultiblockRenderCleanupSavedData.load(data.save(new CompoundTag()));
        assertArrayEquals(expected, restored.save(new CompoundTag()).getLongArray("Positions"));
        assertFalse(restored.isDirty());
    }

    @Test
    void reformedMemberCancelsOnlyItsOwnPendingCleanup() {
        var data = new MultiblockRenderCleanupSavedData();
        var reformed = new BlockPos(16, 64, 0);
        var remaining = reformed.above();
        data.defer(reformed);
        data.defer(remaining);
        data.cancel(reformed);
        var restored = MultiblockRenderCleanupSavedData.load(data.save(new CompoundTag()));
        assertArrayEquals(new long[] {remaining.asLong()}, restored.save(new CompoundTag()).getLongArray("Positions"));
        restored.cancel(remaining);
        assertEquals(0, restored.save(new CompoundTag()).getLongArray("Positions").length);
        assertTrue(restored.isDirty());
    }

    @Test
    void duplicateDeferralsAreIdempotentAndCancellationIsPersistent() {
        var data = new MultiblockRenderCleanupSavedData();
        var pos = new BlockPos(0, -48, 16);
        data.defer(pos);
        data.defer(pos.mutable());
        assertEquals(1, data.save(new CompoundTag()).getLongArray("Positions").length);
        data.cancel(pos);
        var restored = MultiblockRenderCleanupSavedData.load(data.save(new CompoundTag()));
        assertEquals(0, restored.save(new CompoundTag()).getLongArray("Positions").length);
    }
}
