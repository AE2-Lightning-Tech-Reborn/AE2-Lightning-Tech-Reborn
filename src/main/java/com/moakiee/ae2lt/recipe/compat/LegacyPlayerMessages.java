package com.moakiee.ae2lt.recipe.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Preserves the former action-bar choice of Player.displayClientMessage. */
public final class LegacyPlayerMessages {
    private LegacyPlayerMessages() {
    }

    public static void display(Player player, Component message, boolean actionBar) {
        if (actionBar) {
            player.sendOverlayMessage(message);
        } else {
            player.sendSystemMessage(message);
        }
    }
}
