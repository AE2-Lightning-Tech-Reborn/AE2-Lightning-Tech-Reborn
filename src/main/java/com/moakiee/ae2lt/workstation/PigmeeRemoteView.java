package com.moakiee.ae2lt.workstation;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.network.PacketDistributor;

/** Bounded, same-dimension supplement to the real body's native chunk subscription. */
public final class PigmeeRemoteView {
    public static final int RADIUS = 2;
    public static final double MAX_DISTANCE = 1024;
    private final PigmeeControlLeases.Lease lease;
    private final net.minecraft.server.level.ServerLevel level;
    private final Set<Long> queued = new HashSet<>();
    private ChunkPos center;
    private boolean closed;

    PigmeeRemoteView(PigmeeControlLeases.Lease lease) { this.lease = lease; this.level = lease.actor.serverLevel(); }
    public static boolean enabled() { return Boolean.getBoolean("ae2lt.pigmeeRemoteView"); }
    public static boolean separated(ServerPlayer owner, PigmeeServerPlayer actor) {
        // The first slice excludes overlap/transition ownership. Keep both storage regions
        // disjoint, including vanilla's client cache padding and a movement margin.
        var a = owner.chunkPosition(); var b = actor.chunkPosition();
        int margin = owner.server.getPlayerList().getViewDistance() + RADIUS + 5;
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z)) > margin;
    }
    static boolean allowed(ServerPlayer owner, PigmeeServerPlayer actor) {
        return enabled() && owner.level() == actor.level() && owner.distanceToSqr(actor) <= MAX_DISTANCE * MAX_DISTANCE && separated(owner, actor);
    }
    boolean valid() { return !closed && lease.controller.serverLevel() == level && lease.actor.serverLevel() == level
            && lease.controller.isAlive() && !lease.controller.isRemoved() && lease.actor.isAlive() && !lease.actor.isRemoved()
            && allowed(lease.controller, lease.actor); }
    void tick() {
        if (closed) return;
        var owner = lease.controller; var level = lease.actor.serverLevel();
        var next = lease.actor.chunkPosition();
        if (!next.equals(center)) {
            for (long key : Set.copyOf(queued)) {
                var pos = new ChunkPos(key);
                if (!inside(next, pos.x, pos.z)) { drop(pos); queued.remove(key); }
            }
            center = next;
            PacketDistributor.sendToPlayer(owner, new PigmeeRemoteViewPacket(lease.token, true, center.x, center.z));
        }
        int budget = 2;
        // Native player tickets belong to the actor; this view never adds loading tickets.
        // Ready-only access avoids synchronous remote terrain generation on use.
        for (int ring = 0; ring <= RADIUS && budget > 0; ring++) {
            for (int dx = -ring; dx <= ring && budget > 0; dx++) for (int dz = -ring; dz <= ring && budget > 0; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                long key = ChunkPos.asLong(center.x + dx, center.z + dz);
                if (queued.contains(key)) continue;
                var chunk = level.getChunkSource().chunkMap.getChunkToSend(key);
                if (chunk == null) continue;
                queued.add(key);
                owner.connection.chunkSender.markChunkPendingToSend(chunk);
                EventHooks.fireChunkWatch(owner, chunk, level);
                budget--;
            }
        }
        // Refresh stationary entities after initial chunk send, while preserving the body's
        // ordinary native tracking. No camera/position/PlayerList substitution occurs.
        if (owner.server.getTickCount() % 10 == 0) level.getChunkSource().chunkMap.move(owner);
    }
    private static boolean inside(ChunkPos center, int x, int z) {
        return Math.abs(x - center.x) <= RADIUS && Math.abs(z - center.z) <= RADIUS;
    }
    boolean tracks(int x, int z) {
        long key = ChunkPos.asLong(x, z);
        return valid() && queued.contains(key) && !lease.controller.connection.chunkSender.isPending(key);
    }
    public int chunkCount() { return queued.size(); }
    private void drop(ChunkPos pos) {
        var owner = lease.controller;
        if (!owner.getChunkTrackingView().contains(pos)) {
            EventHooks.fireChunkUnWatch(owner, pos, owner.serverLevel());
            owner.connection.chunkSender.dropChunk(owner, pos);
        }
    }
    void close() {
        if (closed) return;
        closed = true;
        var owner = lease.controller;
        if (owner.serverLevel() != level) {
            // Native dimension travel releases before transfer. If a third-party path
            // skipped that hook, preserve all pending coordinates needed by the new body.
            // Removing an actually pending entry sends no forget packet.
            for (long key : queued) {
                var pos = new ChunkPos(key);
                if (!owner.getChunkTrackingView().contains(pos) && owner.connection.chunkSender.isPending(key)) owner.connection.chunkSender.dropChunk(owner, pos);
            }
            queued.clear(); return;
        }
        for (long key : queued) {
            var pos = new ChunkPos(key);
            if (owner.getChunkTrackingView().contains(pos)) {
                // If the body was teleported toward the view, install its native chunk before
                // clearing the supplemental cache. Never let teardown erase body collision.
                var chunk = owner.serverLevel().getChunkSource().chunkMap.getChunkToSend(key);
                if (chunk != null) owner.connection.send(chunk.getAuxLightManager(pos).sendLightDataTo(
                        new ClientboundLevelChunkWithLightPacket(chunk, owner.serverLevel().getLightEngine(), null, null)));
            } else drop(pos);
        }
        queued.clear();
        owner.serverLevel().getChunkSource().chunkMap.move(owner);
        PacketDistributor.sendToPlayer(owner, new PigmeeRemoteViewPacket(lease.token, false, 0, 0));
    }
    public static boolean watches(ServerPlayer viewer, int x, int z) {
        var view = current(viewer);
        return view != null && view.tracks(x, z);
    }
    public static Vec3 trackingPosition(ServerPlayer viewer, Entity target, Vec3 original) {
        var view = current(viewer);
        return view != null && view.tracks(target.chunkPosition().x, target.chunkPosition().z)
                ? view.lease.actor.position() : original;
    }
    private static PigmeeRemoteView current(ServerPlayer viewer) {
        var manager = PigmeeWorkstationRuntime.existingManager(viewer.server);
        return manager == null ? null : manager.controls().remoteView(viewer);
    }
}
