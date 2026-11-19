package com.moakiee.ae2lt.logic.energy;

import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Server-derived, bounded flood fill followed by bounded edits. Never loads chunks or changes ME topology. */
final class WirelessEnergyBulkConnections {
    static final int CLUSTER_LIMIT = 8192;
    static final int SCANS_PER_TICK = 2048;
    static final int EDITS_PER_TICK = 256;
    private static final int MAX_JOBS = 8;
    private final ArrayDeque<Job> jobs = new ArrayDeque<>();
    private final HashSet<UUID> players = new HashSet<>();
    private int lastTick = Integer.MIN_VALUE;

    private record Target(BlockPos pos, BlockEntity expected) {}

    private static final class Job {
        final ServerPlayer player;
        final ServerLevel level;
        final int frequency;
        final boolean bind;
        final Direction preferred;
        final Block block;
        final Consumer<WirelessEnergyEndpoints.BulkResult> completion;
        final ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        final HashSet<BlockPos> seen = new HashSet<>();
        final ArrayList<Target> targets = new ArrayList<>();
        final Map<String, Integer> skipped = new LinkedHashMap<>();
        int cursor, changed, unchanged;
        boolean unloaded;
        Job(ServerPlayer player, int frequency, ServerLevel level, BlockPos seed, Direction preferred,
            boolean bind, Consumer<WirelessEnergyEndpoints.BulkResult> completion) {
            this.player = player; this.level = level; this.frequency = frequency; this.preferred = preferred;
            this.bind = bind; this.completion = completion; this.block = level.getBlockState(seed).getBlock();
            frontier.add(seed.immutable()); seen.add(seed.immutable());
        }
        WirelessEnergyEndpoints.BulkResult result(String error) {
            return new WirelessEnergyEndpoints.BulkResult(bind, changed, unchanged, skipped, unloaded, error);
        }
    }

    WirelessEnergyEndpoints.BulkResult queue(WirelessEnergyEndpoints endpoints, ServerPlayer player, int frequency,
            ServerLevel level, BlockPos seed, Direction preferred,
            Consumer<WirelessEnergyEndpoints.BulkResult> completion) {
        boolean bind = !endpoints.isBound(level, seed, frequency);
        String error = "";
        if (!allowed(player, level, frequency) || !player.canInteractWithBlock(seed, 1)
                || !level.mayInteract(player, seed)
                || level.getChunkSource().getChunkNow(seed.getX() >> 4, seed.getZ() >> 4) == null) error = "no_permission";
        else if (players.contains(player.getUUID()) || jobs.size() >= MAX_JOBS) error = "bulk_busy";
        else if (bind && endpoints.findInputFace(level, seed, preferred) == null) error = "unsupported";
        if (!error.isEmpty()) return new WirelessEnergyEndpoints.BulkResult(bind, 0, 0, Map.of(), false, error);
        var job = new Job(player, frequency, level, seed, preferred, bind, completion);
        players.add(player.getUUID());
        jobs.addLast(job);
        return job.result("bulk_queued");
    }

    private static boolean allowed(ServerPlayer player, ServerLevel level, int frequency) {
        var manager = WirelessFrequencyManager.get(level.getServer());
        var f = manager == null ? null : manager.getFrequency(frequency);
        return !player.isRemoved() && player.serverLevel() == level && !player.isSpectator()
                && player.mayBuild() && f != null && f.getPlayerAccess(player).canUse();
    }

    void tick(WirelessEnergyEndpoints endpoints, MinecraftServer server) {
        if (jobs.isEmpty() || lastTick == server.getTickCount()) return;
        lastTick = server.getTickCount();
        long start = System.nanoTime();
        int scans = 0, edits = 0, blocked = 0;
        while (!jobs.isEmpty() && System.nanoTime() - start < 2_000_000L) {
            var job = jobs.removeFirst();
            String error = "";
            boolean done = false;
            try {
                if (!allowed(job.player, job.level, job.frequency)) {
                    error = "bulk_cancelled"; done = true;
                } else if (!job.frontier.isEmpty() && scans < SCANS_PER_TICK) {
                    scans++; error = scan(job); done = !error.isEmpty(); blocked = 0;
                } else if (job.frontier.isEmpty() && edits < EDITS_PER_TICK) {
                    if (job.cursor < job.targets.size()) { edits++; edit(endpoints, job); }
                    done = job.cursor == job.targets.size(); blocked = 0;
                } else {
                    blocked++;
                }
            } catch (RuntimeException ex) {
                error = "bulk_cancelled"; done = true;
                com.mojang.logging.LogUtils.getLogger().warn("Wireless FE group operation failed", ex);
            }
            if (done) {
                players.remove(job.player.getUUID());
                job.completion.accept(job.result(error));
            } else jobs.addLast(job);
            if (blocked >= jobs.size() || (scans >= SCANS_PER_TICK && edits >= EDITS_PER_TICK)) break;
        }
    }

    private String scan(Job job) {
        var pos = job.frontier.removeFirst();
        var chunk = job.level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) { job.unloaded = true; return ""; }
        if (!chunk.getBlockState(pos).is(job.block)) return "";
        // Complete discovery before any edit, so an oversized group is rejected atomically.
        if (job.targets.size() == CLUSTER_LIMIT) return "cluster_limit";
        job.targets.add(new Target(pos, WirelessEnergyEndpoints.loadedBlockEntity(job.level, pos)));
        for (var direction : Direction.values()) {
            var next = pos.relative(direction);
            if (!job.level.isOutsideBuildHeight(next) && job.seen.add(next)) job.frontier.addLast(next);
        }
        return "";
    }

    private void edit(WirelessEnergyEndpoints endpoints, Job job) {
        var target = job.targets.get(job.cursor++);
        var pos = target.pos();
        var be = WirelessEnergyEndpoints.loadedBlockEntity(job.level, pos);
        if (be == null || be != target.expected() || be.isRemoved() || !be.getBlockState().is(job.block)) {
            job.skipped.merge("target_changed", 1, Integer::sum); return;
        }
        boolean before = endpoints.isBound(job.level, pos, job.frequency);
        var face = job.bind ? endpoints.findInputFace(job.level, pos, job.preferred) : job.preferred;
        if (face == null) { job.skipped.merge("unsupported", 1, Integer::sum); return; }
        String result = endpoints.setBound(job.player, job.frequency, job.level, pos, face, job.bind, "manual", false, target.expected());
        if (!result.equals(job.bind ? "connected" : "disconnected")) job.skipped.merge(result, 1, Integer::sum);
        else if (before == job.bind) job.unchanged++;
        else job.changed++;
    }
}
