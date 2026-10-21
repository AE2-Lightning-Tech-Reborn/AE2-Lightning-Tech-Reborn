package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.PasswordAttemptWindow;
import com.moakiee.ae2lt.grid.WirelessFrequency;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Server-thread only. Weak player keys do not retain disconnected sessions. */
final class FrequencyPasswordAttempts {
    private static final Map<ServerPlayer, PasswordAttemptWindow> WINDOWS = new WeakHashMap<>();

    private FrequencyPasswordAttempts() {}

    static boolean allow(ServerPlayer player, WirelessFrequency frequency, String password) {
        if (frequency.getSecurity() != FrequencySecurityLevel.ENCRYPTED
                || frequency.getPlayerAccess(player).canUse() || password.isEmpty()) return true;
        if (WINDOWS.computeIfAbsent(player, ignored -> new PasswordAttemptWindow()).tryAcquire(System.nanoTime())) {
            return true;
        }
        player.connection.disconnect(Component.translatable("ae2lt.frequency.too_many_password_attempts"));
        return false;
    }
}
