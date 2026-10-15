package com.moakiee.ae2lt.workstation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Ephemeral server-thread leases. Never serialized or inferred from item NBT. */
public final class PigmeeControlLeases {
    public static final double LOCAL_VIEW_LIMIT = 32;
    private final PigmeeActorManager manager;
    private final Map<UUID, Lease> actors = new HashMap<>();
    private final Map<UUID, Lease> owners = new HashMap<>();
    PigmeeControlLeases(PigmeeActorManager manager) { this.manager = manager; }
    public static final class Lease {
        public final UUID token = UUID.randomUUID();
        public final ServerPlayer controller;
        public final PigmeeServerPlayer actor;
        private PigmeeRemoteView remote;
        private int actionSequence = -1, lastActionTick = -1, lastReleaseTick = -1;
        String actionResult = "ready";
        private int deadline, sequence = -1, lastInputTick = -1;
        private Lease(ServerPlayer controller, PigmeeServerPlayer actor) { this.controller = controller; this.actor = actor; deadline = controller.server.getTickCount() + 40; }
    }
    private void checkThread(ServerPlayer player) {
        if (!PigmeeWorkstationRuntime.enabled() || !player.server.isSameThread()) throw new IllegalStateException("Control requires the enabled server thread");
    }
    private boolean authorized(ServerPlayer owner, PigmeeServerPlayer actor) {
        return !owner.isFakePlayer() && !owner.isSpectator() && owner.isAlive() && !owner.isRemoved() && actor.isAlive() && !actor.isRemoved()
                && manager.active(actor.getUUID()) == actor && manager.ownedBy(actor.getUUID(), owner.getUUID())
                && actor.level() == owner.level() && (owner.distanceToSqr(actor) <= LOCAL_VIEW_LIMIT * LOCAL_VIEW_LIMIT || PigmeeRemoteView.allowed(owner, actor))
                && (bound(owner.getMainHandItem(), actor.getUUID()) || bound(owner.getOffhandItem(), actor.getUUID()));
    }
    private static boolean validRange(Lease lease) {
        return lease.remote == null ? lease.controller.distanceToSqr(lease.actor) <= LOCAL_VIEW_LIMIT * LOCAL_VIEW_LIMIT : lease.remote.valid();
    }
    private static boolean bound(net.minecraft.world.item.ItemStack stack, UUID id) {
        return stack.getItem() instanceof PigmeeRemoteControlItem && id.equals(PigmeeRemoteControlItem.bound(stack));
    }
    public Lease acquire(ServerPlayer owner, UUID actorId) {
        checkThread(owner);
        var actor = manager.active(actorId);
        if (actor == null || !authorized(owner, actor)) throw new IllegalStateException("Control needs your active Pigmee, held bound remote, and same dimension; 32-block local range or an enabled, separated remote window up to 1024 blocks");
        var existing = actors.get(actorId);
        if (existing != null && existing.controller != owner) throw new IllegalStateException("Pigmee already controlled");
        releaseOwner(owner); // Renewing creates a new generation; old input can never resume it.
        var lease = new Lease(owner, actor);
        actors.put(actorId, lease); owners.put(owner.getUUID(), lease);
        actor.clearControlInput();
        if (owner.distanceToSqr(actor) > LOCAL_VIEW_LIMIT * LOCAL_VIEW_LIMIT) {
            lease.remote = new PigmeeRemoteView(lease);
            lease.remote.tick();
        }
        return lease;
    }
    public boolean input(ServerPlayer owner, UUID token, int sequence, int flags, float yaw, float pitch) {
        checkThread(owner);
        var lease = owners.get(owner.getUUID());
        if (lease == null || lease.controller != owner || !lease.token.equals(token)) return false;
        if (!authorized(owner, lease.actor) || !validRange(lease) || owner.server.getTickCount() > lease.deadline) { release(lease); return false; }
        if (sequence < 0 || sequence <= lease.sequence || !Float.isFinite(yaw) || !Float.isFinite(pitch) || (flags & ~127) != 0) return false;
        int now = owner.server.getTickCount();
        if (lease.lastInputTick == now) return false; // At most one movement update per server tick.
        lease.sequence = sequence; lease.lastInputTick = now; lease.deadline = now + 40;
        lease.actor.controlInput(flags, yaw, pitch);
        return true;
    }
    /** Actions have a separate replay counter and never renew a movement heartbeat lease. */
    public boolean action(ServerPlayer owner, UUID token, int sequence, int action, int slot, float yaw, float pitch) {
        checkThread(owner);
        var lease = owners.get(owner.getUUID());
        if (lease == null || lease.controller != owner || !lease.token.equals(token)) return false;
        int now = owner.server.getTickCount();
        if (!authorized(owner, lease.actor) || !validRange(lease) || now > lease.deadline) { release(lease); return false; }
        if (sequence < 0 || sequence <= lease.actionSequence || action < 0 || action > PigmeeNativeActions.ATTACK
                || slot < 0 || slot > 8 || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return false;
        // An explicit release must not get lost behind another action in the same tick.
        if (action == PigmeeNativeActions.RELEASE_USE) {
            if (lease.lastReleaseTick == now) return false;
            lease.lastReleaseTick = now;
        } else if (lease.lastActionTick == now) return false;
        lease.actionSequence = sequence;
        lease.lastActionTick = now;
        lease.actor.setYRot(net.minecraft.util.Mth.wrapDegrees(yaw));
        lease.actor.setYHeadRot(lease.actor.getYRot());
        lease.actor.setXRot(net.minecraft.util.Mth.clamp(pitch, -90, 90));
        lease.actionResult = PigmeeNativeActions.perform(lease.actor, action, slot,
                () -> owners.get(owner.getUUID()) == lease && authorized(owner, lease.actor) && validRange(lease));
        if (owners.get(owner.getUUID()) == lease && owner.connection != null)
            PacketDistributor.sendToPlayer(owner, PigmeeControlStatePacket.of(lease, true));
        return true;
    }

    public void release(ServerPlayer owner, UUID token) {
        checkThread(owner); var lease = owners.get(owner.getUUID());
        if (lease != null && lease.controller == owner && lease.token.equals(token)) release(lease);
    }
    public void releaseOwner(ServerPlayer owner) {
        var lease = owners.get(owner.getUUID());
        if (lease != null && lease.controller == owner) release(lease);
    }
    void releaseActor(UUID id) { var lease = actors.get(id); if (lease != null) release(lease); }
    private void release(Lease lease) {
        actors.remove(lease.actor.getUUID(), lease); owners.remove(lease.controller.getUUID(), lease);
        lease.actor.clearControlInput();
        lease.actor.stopUsingItem();
        lease.actor.closeContainer();
        if (lease.remote != null) lease.remote.close();
        if (lease.controller.connection != null) PacketDistributor.sendToPlayer(lease.controller, PigmeeControlStatePacket.of(lease, false));
    }
    void tick() {
        for (var lease : new ArrayList<>(actors.values())) {
            int now = lease.controller.server.getTickCount();
            if (!authorized(lease.controller, lease.actor) || !validRange(lease) || now > lease.deadline) { release(lease); continue; }
            if (lease.remote != null) lease.remote.tick();
            // Missing heartbeat stops intent promptly, while native gravity/hunger/damage continue.
            if (now - lease.lastInputTick > 5) { lease.actor.clearControlInput(); lease.actor.stopUsingItem(); }
            if (now % 10 == 0 && lease.controller.connection != null) PacketDistributor.sendToPlayer(lease.controller, PigmeeControlStatePacket.of(lease, true));
        }
    }
    PigmeeRemoteView remoteView(ServerPlayer viewer) {
        var lease = owners.get(viewer.getUUID());
        return lease != null && lease.controller == viewer ? lease.remote : null;
    }
    public int count() { return actors.size(); }
}
