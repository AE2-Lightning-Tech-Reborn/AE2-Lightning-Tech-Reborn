package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.client.gui.FrequencyScreen;
import net.minecraft.client.Minecraft;

/** Client-only presentation of the frequency response packet. */
final class FrequencyResponseClientBridge {
    private FrequencyResponseClientBridge() {}

    static void show(FrequencyResponsePacket packet) {
        var client = Minecraft.getInstance();
        if (client.player == null) return;
        var message = packet.toMessage();
        if (client.screen instanceof FrequencyScreen screen) {
            screen.showInlineError(message);
        } else {
            com.moakiee.ae2lt.recipe.compat.LegacyPlayerMessages.display(
                    client.player, message, true);
        }
    }
}
