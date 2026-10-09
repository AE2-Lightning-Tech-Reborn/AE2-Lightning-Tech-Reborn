package com.moakiee.ae2lt.network.railgun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.UUID;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RailgunBeamPacketCodecTest {
    @Test
    void updatePacketsPreserveBothModeAndActiveFlags() {
        UUID shooter = UUID.randomUUID();
        Vec3 from = new Vec3(1.0D, 2.0D, 3.0D);
        Vec3 to = new Vec3(4.0D, 5.0D, 6.0D);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (boolean ehv : new boolean[] {false, true}) {
                for (boolean active : new boolean[] {false, true}) {
                    var packet = new RailgunBeamUpdatePacket(shooter, from, to, active, ehv);
                    packet.write(buffer);
                    assertEquals(66, buffer.readableBytes());
                    assertEquals(packet, RailgunBeamUpdatePacket.decode(buffer));
                    assertFalse(buffer.isReadable());
                    buffer.clear();
                }
            }
        } finally {
            buffer.release();
        }
    }

    @Test
    void chainPacketsPreserveModeSoundAndSegmentPairs() {
        UUID shooter = UUID.randomUUID();
        Vec3 first = new Vec3(1.0D, 2.0D, 3.0D);
        Vec3 next = new Vec3(4.0D, 5.0D, 6.0D);
        Vec3 last = new Vec3(7.0D, 8.0D, 9.0D);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (List<Vec3> path : List.of(List.<Vec3>of(), List.of(first, next, next, last))) {
                for (boolean ehv : new boolean[] {false, true}) {
                    for (boolean soundEnabled : new boolean[] {false, true}) {
                        var packet = new RailgunBeamChainFxPacket(shooter, first, path, soundEnabled, ehv);
                        packet.write(buffer);
                        assertEquals(43 + path.size() * 24, buffer.readableBytes());
                        assertEquals(packet, RailgunBeamChainFxPacket.decode(buffer));
                        assertFalse(buffer.isReadable());
                        buffer.clear();
                    }
                }
            }
        } finally {
            buffer.release();
        }
    }
}
