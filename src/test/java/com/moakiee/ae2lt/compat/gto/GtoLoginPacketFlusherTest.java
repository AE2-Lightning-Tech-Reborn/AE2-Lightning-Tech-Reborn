package com.moakiee.ae2lt.compat.gto;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import org.junit.jupiter.api.Test;

class GtoLoginPacketFlusherTest {
    @Test
    void loginWritesBecomeReadableWithoutAnotherPacket() {
        var channel = new EmbeddedChannel();
        try {
            channel.attr(Connection.ATTRIBUTE_PROTOCOL).set(ConnectionProtocol.LOGIN);
            channel.write("login query");
            assertNull(channel.readOutbound());
            GtoLoginPacketFlusher.flushLogin(channel);
            assertEquals("login query", channel.readOutbound());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void playWritesKeepTheirExistingFlushBoundary() {
        var channel = new EmbeddedChannel();
        try {
            channel.attr(Connection.ATTRIBUTE_PROTOCOL).set(ConnectionProtocol.PLAY);
            channel.write("chunk batch");
            GtoLoginPacketFlusher.flushLogin(channel);
            assertNull(channel.readOutbound());
            channel.flush();
            assertEquals("chunk batch", channel.readOutbound());
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
