package qa;

import java.lang.reflect.Field;
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
                LOGGER.info("GTO_LOGIN_TRACE channel={} active={} autoRead={} writable={} pendingBytes={} pipeline={}",
                        channel.id(), channel.isActive(), channel.config().isAutoRead(), channel.isWritable(),
                        buffer == null ? -1 : buffer.totalPendingWriteBytes(), channel.pipeline().names());
            });
        }
    }
}
