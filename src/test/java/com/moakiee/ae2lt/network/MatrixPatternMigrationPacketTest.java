package com.moakiee.ae2lt.network;

import static org.junit.jupiter.api.Assertions.*;

import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationSnapshot;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class MatrixPatternMigrationPacketTest {
    @Test void actionPacketsPreserveTokenPositionAndStartStopAcrossWorldBounds() {
        for (var pos : new BlockPos[] {BlockPos.ZERO, new BlockPos(-30000000, -64, 30000000),
                new BlockPos(30000000, 319, -30000000)}) {
            for (int token : new int[] {0, 1, 127, 128, Integer.MAX_VALUE}) {
                for (boolean stop : new boolean[] {false, true}) {
                    var expected = new MatrixPatternMigrationActionPacket(token, pos, stop);
                    var buffer = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        MatrixPatternMigrationActionPacket.encode(expected, buffer);
                        assertEquals(expected, MatrixPatternMigrationActionPacket.decode(buffer));
                        assertEquals(0, buffer.readableBytes());
                    } finally { buffer.release(); }
                }
            }
        }
    }

    @Test void statusPacketsPreserveEveryEnumAndLongCounterWithoutTruncation() {
        long base = 1L << 40;
        for (var stage : PatternMigrationSnapshot.Stage.values()) {
            for (var reason : PatternMigrationSnapshot.Reason.values()) {
                var snapshot = new PatternMigrationSnapshot(stage, reason, 3,
                        base + 1, base + 2, base + 3, base + 4, base + 5, base + 6,
                        base + 7, base + 8, base + 9, base + 10, base + 11, Long.MAX_VALUE);
                var expected = new MatrixPatternMigrationStatusPacket(Integer.MAX_VALUE, snapshot);
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    MatrixPatternMigrationStatusPacket.encode(expected, buffer);
                    assertEquals(expected, MatrixPatternMigrationStatusPacket.decode(buffer));
                    assertEquals(0, buffer.readableBytes());
                } finally { buffer.release(); }
            }
        }
    }

    @Test void idleSnapshotRoundTripsWithoutInventingAnActiveTask() {
        var expected = new MatrixPatternMigrationStatusPacket(0, PatternMigrationSnapshot.IDLE);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MatrixPatternMigrationStatusPacket.encode(expected, buffer);
            var decoded = MatrixPatternMigrationStatusPacket.decode(buffer);
            assertEquals(expected, decoded); assertFalse(decoded.snapshot().active());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
}
