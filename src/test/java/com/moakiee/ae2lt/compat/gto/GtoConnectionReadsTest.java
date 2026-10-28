package com.moakiee.ae2lt.compat.gto;

import static java.nio.channels.SelectionKey.OP_READ;
import static org.junit.jupiter.api.Assertions.*;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GtoConnectionReadsTest {
    @Test
    void queuedClearReproducesTheLostReadAndProtocolRepairRestoresRealTcpDelivery() throws Exception {
        try (var wire = new Wire()) {
            wire.protocolTransition(false);
            assertTrue(wire.client.config().isAutoRead());
            assertEquals(0, wire.readInterest(), "the unmodified Netty ordering must reproduce the failure");

            wire.protocolTransition(true);
            assertEquals(OP_READ, wire.readInterest());
            wire.deliverByte();
        }
    }

    @Test
    void intentionalPauseAfterSchedulingTheRepairStillWins() throws Exception {
        try (var wire = new Wire()) {
            wire.client.eventLoop().submit(() -> {
                GtoConnectionReads.afterProtocolChange(wire.client);
                wire.client.config().setAutoRead(false);
            }).sync();
            assertEquals(0, wire.readInterest());
            assertFalse(wire.client.config().isAutoRead(), "the repair must not undo an intentional pause");
            wire.client.eventLoop().submit(() -> wire.client.config().setAutoRead(true)).sync();
            wire.deliverByte();
        }
    }

    @Test
    void absentAndClosedChannelsAreIgnored() {
        assertDoesNotThrow(() -> GtoConnectionReads.afterProtocolChange(null));
        var channel = new EmbeddedChannel();
        channel.close();
        try { assertDoesNotThrow(() -> GtoConnectionReads.afterProtocolChange(channel)); }
        finally { channel.finishAndReleaseAll(); }
    }

    private static final class Probe extends NioSocketChannel {
        int readInterest() { return selectionKey().interestOps() & OP_READ; }
    }

    private static final class Wire implements AutoCloseable {
        private final NioEventLoopGroup serverLoop = new NioEventLoopGroup(1);
        private final NioEventLoopGroup clientLoop = new NioEventLoopGroup(1);
        private final AtomicReference<Channel> peer = new AtomicReference<>();
        private final CountDownLatch accepted = new CountDownLatch(1);
        private final CountDownLatch received = new CountDownLatch(1);
        private Channel server;
        private Probe client;

        Wire() throws Exception {
            try {
                server = new ServerBootstrap().group(serverLoop).channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override protected void initChannel(SocketChannel channel) {
                                peer.set(channel);
                                channel.pipeline().addLast(new ChannelInboundHandlerAdapter());
                                accepted.countDown();
                            }
                        }).bind(new InetSocketAddress("127.0.0.1", 0)).sync().channel();
                client = (Probe) new Bootstrap().group(clientLoop).channelFactory(Probe::new)
                        .handler(new SimpleChannelInboundHandler<ByteBuf>() {
                            @Override protected void channelRead0(ChannelHandlerContext context, ByteBuf bytes) {
                                if (bytes.readByte() == 37) received.countDown();
                            }
                        }).connect(server.localAddress()).sync().channel();
                assertTrue(accepted.await(5, TimeUnit.SECONDS));
            } catch (Exception | AssertionError failure) {
                close();
                throw failure;
            }
        }

        void protocolTransition(boolean repair) throws Exception {
            var entered = new CountDownLatch(1);
            var resume = new CountDownLatch(1);
            var transition = client.eventLoop().submit(() -> {
                entered.countDown();
                assertTrue(resume.await(5, TimeUnit.SECONDS));
                client.config().setAutoRead(true);
                if (repair) GtoConnectionReads.afterProtocolChange(client);
                return null;
            });
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                // The event loop is still inside the transition, so Netty must queue its old clear.
                client.config().setAutoRead(false);
            } finally { resume.countDown(); }
            transition.sync();
        }

        int readInterest() throws Exception {
            return client.eventLoop().submit(client::readInterest).sync().getNow();
        }

        void deliverByte() throws Exception {
            peer.get().writeAndFlush(Unpooled.wrappedBuffer(new byte[] {37})).sync();
            assertTrue(received.await(5, TimeUnit.SECONDS), "a real TCP byte must reach the resumed reader");
        }

        @Override public void close() {
            if (client != null) client.close().syncUninterruptibly();
            if (peer.get() != null) peer.get().close().syncUninterruptibly();
            if (server != null) server.close().syncUninterruptibly();
            serverLoop.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
            clientLoop.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }
}
