package com.moakiee.ae2lt.compat.gto;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;

/** Retains vanilla's periodic flush while Forge is negotiating a login. */
public final class GtoLoginPacketFlusher {
    private GtoLoginPacketFlusher() { }

    public static void flushLogin(Channel channel) {
        if (channel != null && channel.isActive()
                && channel.attr(Connection.ATTRIBUTE_PROTOCOL).get() == ConnectionProtocol.LOGIN) {
            channel.flush();
        }
    }
}
