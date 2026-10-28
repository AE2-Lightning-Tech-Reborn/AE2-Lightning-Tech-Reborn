package qa;

import java.lang.reflect.Field;
import java.nio.channels.SelectionKey;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;

/** Opt-in channel state only; never logs packet contents or account data. */
final class GtoNetworkDiagnostics {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    static void inspect(Object owner) {
        if (owner == null || !Boolean.getBoolean("ae2lt.gtoLoginTrace")) return;
        LOGGER.info("GTO_LOGIN_TRACE owner={}", owner.getClass().getName());
        try {
            for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                    if (Connection.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        channel((Connection) field.get(owner));
                    } else if (java.util.List.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object value = field.get(owner);
                        if (value instanceof java.util.List<?> entries) {
                            for (Object entry : entries.toArray()) if (entry instanceof Connection connection) channel(connection);
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LOGGER.warn("GTO_LOGIN_TRACE inspection failed", failure);
        }
    }
    private static void channel(Connection connection) throws ReflectiveOperationException {
        if (connection == null) return;
        for (Field field : Connection.class.getDeclaredFields()) {
            if (!Channel.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            Channel channel = (Channel) field.get(connection);
            if (channel == null) continue;
            channel.eventLoop().execute(() -> {
                var buffer = channel.unsafe().outboundBuffer();
                LOGGER.info("GTO_LOGIN_TRACE channel={} active={} protocol={} autoRead={} nio={} writable={} pendingBytes={} pipeline={}",
                        channel.id(), channel.isActive(), channel.attr(Connection.ATTRIBUTE_PROTOCOL).get(),
                        channel.config().isAutoRead(), nioState(channel), channel.isWritable(),
                        buffer == null ? -1 : buffer.totalPendingWriteBytes(), channel.pipeline().names());
            });
        }
    }

    private static String nioState(Channel channel) {
        String interest = "unregistered", pending = "unknown";
        try {
            for (Class<?> type = channel.getClass(); type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (SelectionKey.class.isAssignableFrom(field.getType()) && field.trySetAccessible()) {
                        var key = (SelectionKey) field.get(channel);
                        if (key != null && key.isValid()) interest = Integer.toString(key.interestOps());
                    } else if (field.getName().equals("readPending") && field.trySetAccessible()) {
                        pending = String.valueOf(field.getBoolean(channel));
                    }
                }
            }
            return "interestOps=" + interest + ",readPending=" + pending;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return failure.getClass().getSimpleName();
        }
    }
}
