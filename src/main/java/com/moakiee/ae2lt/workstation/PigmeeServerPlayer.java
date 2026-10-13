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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;

/** A server-owned body, not a logged-in user or NeoForge's inert FakePlayer. */
public final class PigmeeServerPlayer extends ServerPlayer {
    private final UUID owner;
    private final PigmeeVariant variant;
    private int walkingTicks;
    private int controlFlags;
    private boolean deathSettled;
    private long bodyTicks;

    PigmeeServerPlayer(ServerLevel level, UUID id, UUID owner, PigmeeVariant variant) {
        super(level.getServer(), level,
                new GameProfile(id, "[Pigmee_" + id.toString().substring(0, 6) + "]"),
                ClientInformation.createDefault());
        this.owner = owner;
        this.variant = variant;
        this.connection = new LocalListener(this);
    }

    // The vanilla constructor searches the world's spawn before loading the saved position.
    @Override
    public BlockPos adjustSpawnLocation(ServerLevel level, BlockPos pos) {
        return pos;
    }

    public PigmeeVariant variant() { return variant; }
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

    void controlInput(int flags, float yaw, float pitch) {
        walkingTicks = 0;
        controlFlags = flags;
        setYRot(net.minecraft.util.Mth.wrapDegrees(yaw));
        setYHeadRot(getYRot());
        setXRot(net.minecraft.util.Mth.clamp(pitch, -90, 90));
    }

    boolean hasControlInput() { return controlFlags != 0; }

    void clearControlInput() {
        controlFlags = 0; walkingTicks = 0; xxa = 0; zza = 0;
        setJumping(false); setShiftKeyDown(false); setSprinting(false);
    }

    @Override
    public void tick() {
        super.tick();
        // Real players receive Player.tick() from their connection. This connection is never
        // registered with ServerConnectionListener: the world is the only tick owner here.
        zza = walkingTicks-- > 0 && isAlive() ? 1.0F : 0.0F;
        if (walkingTicks < 0) walkingTicks = 0;
        if (controlFlags != 0 && isAlive()) {
            zza = ((controlFlags & 1) != 0 ? 1 : 0) - ((controlFlags & 2) != 0 ? 1 : 0);
            xxa = ((controlFlags & 4) != 0 ? 1 : 0) - ((controlFlags & 8) != 0 ? 1 : 0);
        } else xxa = 0;
        setJumping((controlFlags & 16) != 0 && isAlive());
        setShiftKeyDown((controlFlags & 32) != 0);
        setSprinting((controlFlags & 64) != 0 && zza > 0);
        doTick();
        bodyTicks++;
        serverLevel().getChunkSource().move(this);
    }

    @Override
    protected void checkFallDamage(double deltaY, boolean grounded, BlockState state, BlockPos pos) {
        // Vanilla ServerPlayer delegates this to movement packets; this body has none.
        doCheckFallDamage(0, deltaY, 0, grounded);
    }

    @Override
    public void die(DamageSource cause) {
        if (deathSettled) return;
        super.die(cause);
        // Vanilla sets lastDeathLocation only after the cancellable death hook and drops.
        deathSettled = getLastDeathLocation().isPresent() && isDeadOrDying();
    }

    void detach() {
        clearControlInput();
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
