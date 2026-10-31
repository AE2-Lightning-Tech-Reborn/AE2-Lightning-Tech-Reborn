package com.moakiee.ae2lt.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

/** Fired on the game bus when a server player's mysterious cell is inspected. */
public final class MysteriousCellInspectEvent extends Event {
    private final ServerPlayer player;
    private final ItemStack stack;
    public MysteriousCellInspectEvent(ServerPlayer player, ItemStack stack) {
        this.player = player;
        this.stack = stack;
    }
    public ServerPlayer player() { return player; }
    /** The inspected stack; listeners should not modify it. */
    public ItemStack stack() { return stack; }
}
