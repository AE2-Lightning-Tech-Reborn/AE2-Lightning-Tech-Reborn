package com.moakiee.ae2lt.workstation;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;

/** A server-owned body, not a logged-in user or NeoForge's inert FakePlayer. */
public final class PigmeeServerPlayer extends ServerPlayer {
    private final UUID owner;
    private int walkingTicks;
    private boolean deathSettled;
    private long bodyTicks;

    PigmeeServerPlayer(ServerLevel level, UUID id, UUID owner) {
        super(level.getServer(), level,
                new GameProfile(id, "[Pigmee_" + id.toString().substring(0, 6) + "]"),
                ClientInformation.createDefault());
        this.owner = owner;
        this.connection = new LocalListener(this);
    }

    // The vanilla constructor searches the world's spawn before loading the saved position.
    @Override
    public BlockPos adjustSpawnLocation(ServerLevel level, BlockPos pos) {
        return pos;
    }

    public UUID owner() { return owner; }
    public long bodyTicks() { return bodyTicks; }
    public boolean deathSettled() { return deathSettled; }

    @Override
    public boolean isFakePlayer() { return true; }

    @Override
    protected int getPermissionLevel() { return 0; }

    @Override
    public int requestedViewDistance() { return 2; }

    @Override
    public boolean canChangeDimensions(Level from, Level to) { return false; }

    @Override
    public Entity changeDimension(DimensionTransition transition) { return null; }

    /** Bounded diagnostic input. No intention survives stop/start or a server restart. */
    public void walkFor(int ticks) {
        if (ticks < 0 || ticks > 200) throw new IllegalArgumentException("walk ticks must be 0..200");
        walkingTicks = ticks;
    }

    @Override
    public void tick() {
        super.tick();
        // Real players receive Player.tick() from their connection. This connection is never
        // registered with ServerConnectionListener: the world is the only tick owner here.
        zza = walkingTicks-- > 0 && isAlive() ? 1.0F : 0.0F;
        if (walkingTicks < 0) walkingTicks = 0;
        doTick();
        bodyTicks++;
        serverLevel().getChunkSource().move(this);
    }

    @Override
    public void die(DamageSource cause) {
        if (deathSettled) return;
        super.die(cause);
        // Vanilla sets lastDeathLocation only after the cancellable death hook and drops.
        deathSettled = getLastDeathLocation().isPresent() && isDeadOrDying();
    }

    void detach() {
        walkFor(0);
        closeContainer();
        getAdvancements().stopListening();
        getTextFilter().leave();
        serverLevel().removePlayerImmediately(this, RemovalReason.UNLOADED_WITH_PLAYER);
    }

    /** No socket, login, keepalive, input packet dispatch, or queued outgoing packets. */
    private static final class LocalListener extends ServerGamePacketListenerImpl {
        LocalListener(PigmeeServerPlayer player) {
            super(player.server, new LocalConnection(), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override public void tick() { }
        @Override public void send(Packet<?> packet) { }
        @Override public void send(Packet<?> packet, PacketSendListener listener) { }
        @Override public void disconnect(Component reason) { player.discard(); }
        @Override public void disconnect(DisconnectionDetails details) { player.discard(); }
    }

    private static final class LocalConnection extends Connection {
        LocalConnection() { super(PacketFlow.SERVERBOUND); }
        @Override public void send(Packet<?> packet) { }
        @Override public void send(Packet<?> packet, PacketSendListener listener) { }
        @Override public void send(Packet<?> packet, PacketSendListener listener, boolean flush) { }
    }
}
