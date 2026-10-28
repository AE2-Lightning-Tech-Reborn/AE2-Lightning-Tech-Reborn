package com.moakiee.ae2lt.compat.gto;

import io.netty.channel.Channel;

/** Orders protocol read resumption after Netty's previously queued read-clear tasks. */
public final class GtoConnectionReads {
    private GtoConnectionReads() { }

    public static void afterProtocolChange(Channel channel) {
        if (channel == null || !channel.isActive()) return;
        // Always enqueue, even on the event loop: an older off-thread setAutoRead(false)
        // may still have a clearReadPending task waiting behind the current callback.
        channel.eventLoop().execute(() -> {
            // A later intentional pause or disconnect must still take precedence.
            if (channel.isActive() && channel.config().isAutoRead()) channel.read();
        });
    }
}
