package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moakiee.ae2lt.network.EasterEggPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

class EasterEggAudienceTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private final GlobalPos source = GlobalPos.of(Level.OVERWORLD, new BlockPos(-100, 64, 200));
    private final Vec3 center = Vec3.atCenterOf(source.pos());

    @Test
    void nearbyOnlyIncludingVerticalDistanceAndDimension() {
        assertTrue(EasterEggAudience.includes(source, Level.OVERWORLD, center));
        assertTrue(EasterEggAudience.includes(source, Level.OVERWORLD, center.add(8, 0, 0)));
        assertFalse(EasterEggAudience.includes(source, Level.OVERWORLD, center.add(8.01, 0, 0)));
        assertFalse(EasterEggAudience.includes(source, Level.OVERWORLD, center.add(0, 9, 0)));
        assertFalse(EasterEggAudience.includes(source, Level.OVERWORLD, center.add(6, 0, 6)));
        assertFalse(EasterEggAudience.includes(source, Level.NETHER, center));
    }

    @Test
    void packetPreservesOrigin() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            EasterEggPacket.encode(new EasterEggPacket(source), buffer);
            assertEquals(source, EasterEggPacket.decode(buffer).source());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
