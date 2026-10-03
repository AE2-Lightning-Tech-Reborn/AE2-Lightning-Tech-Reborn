package com.moakiee.ae2lt.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrequencyCardLinkShortcutPacketTest {
    @Test void forgePacketRoundTripRetainsTarget() {
        var packet = new FrequencyCardLinkShortcutPacket(new BlockPos(-8, 64, 30),
                Direction.WEST, new Vec3(-8, 64.25, 30.75));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.write(buffer);
            assertEquals(packet, FrequencyCardLinkShortcutPacket.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    @Test void targetBoundsRejectSpoofedAndNonFiniteHitPositions() {
        var pos = new BlockPos(-8, 64, 30);
        for (var hit : new Vec3[]{new Vec3(-8, 64, 30), new Vec3(-7, 65, 31)}) {
            assertTrue(new FrequencyCardLinkShortcutPacket(pos, Direction.UP, hit).hitInsideBlock());
        }
        for (var hit : new Vec3[]{new Vec3(-6, 64, 30), new Vec3(-8, 62, 30),
                new Vec3(-8, 64, 33), new Vec3(Double.NaN, 64, 30),
                new Vec3(-8, Double.POSITIVE_INFINITY, 30)}) {
            assertFalse(new FrequencyCardLinkShortcutPacket(pos, Direction.UP, hit).hitInsideBlock());
        }
    }
}
