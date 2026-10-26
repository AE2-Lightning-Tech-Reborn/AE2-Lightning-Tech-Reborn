package com.moakiee.ae2lt.logic.energy;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.logic.management.PowerTelemetry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/** FE endpoints never create grid nodes/connections. All access is on the server thread. */
public final class WirelessEnergyEndpoints extends SavedData {
    private static final int WORLD_LIMIT = 8192;
    private static final int BATCH_SIZE = 32;
    public record BulkResult(boolean bind, int changed, int unchanged, Map<String, Integer> skippedReasons,
                             boolean unloaded, String error) {
        public BulkResult {
            skippedReasons = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(skippedReasons));
        }
        public int skipped() { return skippedReasons.values().stream().mapToInt(Integer::intValue).sum(); }
    }
    public record Location(ResourceKey<Level> dimension, BlockPos pos) {
        public Location { pos = pos.immutable(); }
    }
    public enum State { WAITING, UNLOADED, NO_CONTROLLER, NO_PERMISSION, NO_FE, BLOCKED, ACTIVE, FAULTED }
    public record View(UUID id, int frequency, Location location, Direction face, UUID actor,
                       State state, long lastVisitedTick, long lastDelivered, long totalDelivered, String block) {}
    private static final class Endpoint {
        final UUID id;
        final int frequency;
        final Location location;
        final Direction face;
        final UUID actor;
        final String block, type;
        // Removal invalidates this generation before any later callback can use it.
        boolean registered = true;
        String source = "manual";
        State state = State.WAITING;
        int scheduleDelay = 1;
        long nextVisitTick;
        long lastVisitedTick = Long.MIN_VALUE, lastDelivered, totalDelivered;
        // Registry singletons for the identity check, resolved once per session; null until resolved.
        transient Block blockInstance;
        transient BlockEntityType<?> typeInstance;
        transient boolean identityResolved;
        transient PowerTelemetry.Key telemetryKey;
        // Direct meter handle; re-resolved when stale or when the dispatch context's meter table changed.
        transient PowerTelemetry.Meter telemetryMeter;
        transient Map<PowerTelemetry.Key, PowerTelemetry.Meter> telemetryMeterOwner;
        // Listener-backed FE capability resolution; NeoForge invalidation re-resolves it.
        transient BlockCapabilityCache<IEnergyStorage, Direction> capabilityCache;
        final transient EndpointTargetAccess access = new EndpointTargetAccess();
        Endpoint(UUID id, int frequency, Location location, Direction face, UUID actor, String block, String type) {
            this.id = id; this.frequency = frequency; this.location = location; this.face = face;
            this.actor = actor; this.block = block; this.type = type;
        }
        void bindIdentity(BlockEntity be) {
            blockInstance = be.getBlockState().getBlock();
            typeInstance = be.getType();
            identityResolved = true;
        }
        /** Same-generation check. Registry entries are session singletons, so identity equals the persisted key strings. */
        boolean matches(BlockEntity be) {
            if (!identityResolved) {
                identityResolved = true;
                var blockId = ResourceLocation.tryParse(block);
                var typeId = ResourceLocation.tryParse(type);
                blockInstance = blockId == null ? null
                        : BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
                typeInstance = typeId == null ? null
                        : BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(typeId).orElse(null);
            }
            if (blockInstance != null && typeInstance != null) {
                return be.getBlockState().getBlock() == blockInstance && be.getType() == typeInstance;
            }
            // Entry no longer in the registries (mod removed); keep comparing by key string.
            return block.equals(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString())
                    && type.equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString());
        }
        PowerTelemetry.Key telemetry() {
            var t = telemetryKey;
            if (t == null) {
                t = new PowerTelemetry.Key(PowerTelemetry.Kind.WIRELESS,
                        new PowerTelemetry.Target(location.dimension().location().toString(),
                                location.pos(), face.ordinal()), id);
                telemetryKey = t;
            }
            return t;
        }
        View view() { return new View(id, frequency, location, face, actor, state, lastVisitedTick, lastDelivered, totalDelivered, block); }
    }

    private static final class EndpointTargetAccess implements TargetAccess {
        IEnergyStorage target;
        @Override public long simulateReceive(long max) { return target.receiveEnergy((int) Math.min(Integer.MAX_VALUE, max), true); }
        @Override public long receive(long amount) { return target.receiveEnergy((int) amount, false); }
    }

    /** One endpoint's validated demand, awaiting the per-frequency batch dispatch. */
    private static final class Pending {
        Endpoint endpoint;
        com.moakiee.ae2lt.grid.WirelessFrequency frequency;
        ServerLevel level;
        BlockEntity blockEntity;
        IEnergyStorage target;
        long demand;
        void prepare(Endpoint endpoint, com.moakiee.ae2lt.grid.WirelessFrequency frequency, ServerLevel level,
                     BlockEntity blockEntity, IEnergyStorage target, long demand) {
            this.endpoint = endpoint; this.frequency = frequency; this.level = level;
            this.blockEntity = blockEntity; this.target = target; this.demand = demand;
        }
        void clear() {
            endpoint = null; frequency = null; level = null; blockEntity = null; target = null;
            demand = 0;
        }
    }

    /**
     * Per-tick, per-frequency controller snapshot: node, budget service,
     * escrow and one guarded ME batch buffer shared by every visit of that
     * frequency this tick. Rebuilt whenever the controller's grid or the
     * grid's inventory reference changes mid-tick.
     */
    private static final class DispatchContext {
        final AdvancedWirelessOverloadedControllerBlockEntity controller;
        final int frequency;
        final IGridNode node;
        final IGrid grid;
        final WirelessEnergyGridService service;
        final EnergyEscrow escrow;
        final IActionSource source;
        final appeng.api.storage.MEStorage inventory;
        final GuardedEnergyStorage guarded;
        final BufferedMEStorage buffer;
        final Map<PowerTelemetry.Key, PowerTelemetry.Meter> telemetryMeters;
        final ArrayList<Pending> pending = new ArrayList<>();
        long totalDemand;
        DispatchContext(AdvancedWirelessOverloadedControllerBlockEntity controller, int frequency, IGridNode node,
                        IGrid grid, WirelessEnergyGridService service, EnergyEscrow escrow, IActionSource source,
                        appeng.api.storage.MEStorage inventory, GuardedEnergyStorage guarded, BufferedMEStorage buffer,
                        Map<PowerTelemetry.Key, PowerTelemetry.Meter> telemetryMeters) {
            this.controller = controller; this.frequency = frequency; this.node = node; this.grid = grid;
            this.service = service; this.escrow = escrow; this.source = source; this.inventory = inventory;
            this.guarded = guarded; this.buffer = buffer; this.telemetryMeters = telemetryMeters;
        }
        boolean valid() {
            if (controller.isRemoved() || controller.getFrequencyId() != frequency) return false;
            var currentGrid = controller.getMainNode().getGrid();
            return currentGrid == grid && controller.getMainNode().isActive()
                    && currentGrid.getStorageService().getInventory() == inventory;
        }
    }
    private final Map<Location, Endpoint> endpoints = new LinkedHashMap<>();
    private final WirelessEnergyBulkConnections bulkConnections = new WirelessEnergyBulkConnections();
    private final WirelessEnergySchedule<Endpoint> schedule = new WirelessEnergySchedule<>(e -> e.nextVisitTick);
    private long lastTick = Long.MIN_VALUE;
    private boolean scheduleDirty = true;
    private boolean ticking;
    // Reused across ticks; dispatch contexts and pending lists are rebuilt every tick.
    private final ArrayList<Endpoint> visitScratch = new ArrayList<>();
    private final Pending[] pendingScratch = new Pending[BATCH_SIZE];
    private int pendingCount;
    {
        for (int i = 0; i < pendingScratch.length; i++) pendingScratch[i] = new Pending();
    }
    private final Map<Integer, DispatchContext> contexts = new HashMap<>();
    private final ArrayList<DispatchContext> batches = new ArrayList<>();
    private static WirelessEnergyEndpoints instance;
    private static MinecraftServer owner;

    public static WirelessEnergyEndpoints get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Server thread required");
        if (owner != server) {
            owner = server;
            instance = server.overworld().getDataStorage().computeIfAbsent(
                    new Factory<>(WirelessEnergyEndpoints::new, WirelessEnergyEndpoints::load), "ae2lt_wireless_energy_endpoints");
        }
        return instance;
    }
    public static void stop() { instance = null; owner = null; }

    /** Called only on actual BE removal, not chunk unload: same-type replacements lose the old generation. */
    public static void onRemoved(ServerLevel level, BlockPos pos) {
        if (instance != null && owner == level.getServer()) instance.remove(new Location(level.dimension(), pos));
        WirelessEnergyAdmissions.onRemoved(level, pos);
    }
    private void remove(Location location) {
        var removed = endpoints.remove(location);
        if (removed != null) { removed.registered = false; scheduleDirty = true; setDirty(); }
    }
    public List<View> inspect(int frequency, UUID viewer) {
        var manager = WirelessFrequencyManager.get();
        var f = manager == null ? null : manager.getFrequency(frequency);
        if (f == null || !f.getPlayerAccess(viewer).canUse()) return List.of();
        return endpoints.values().stream().filter(e -> e.frequency == frequency).map(Endpoint::view).toList();
    }
    public @Nullable View inspectTarget(ServerLevel level, BlockPos pos, UUID viewer) {
        var e = endpoints.get(new Location(level.dimension(), pos));
        if (e == null) return null;
        var manager = WirelessFrequencyManager.get(level.getServer());
        var f = manager == null ? null : manager.getFrequency(e.frequency);
        return f != null && f.getPlayerAccess(viewer).canUse() ? e.view() : null;
    }
    /** Removes exactly the displayed generation, including unloaded targets, without touching FE accounts. */
    public String disconnect(ServerPlayer player, int frequency, UUID endpointId) {
        var manager = WirelessFrequencyManager.get(player.server);
        var f = manager == null ? null : manager.getFrequency(frequency);
        if (player.isSpectator() || !player.mayBuild() || f == null
                || !f.getPlayerAccess(player).canUse()) return "no_permission";
        var endpoint = endpoints.values().stream()
                .filter(e -> e.frequency == frequency && e.id.equals(endpointId)).findFirst().orElse(null);
        if (endpoint == null) return "disconnected"; // A stale ID must never remove a replacement.
        if (!endpoint.actor.equals(player.getUUID()) && !f.getPlayerAccess(player).isManager()) return "no_permission";
        WirelessEnergyAdmissions.get(player.server).manualOverride(endpoint.location, endpoint.actor,
                frequency, endpoint.face);
        remove(endpoint.location);
        return "disconnected";
    }

    public boolean isBound(ServerLevel level, BlockPos pos, int frequency) {
        var e = endpoints.get(new Location(level.dimension(), pos));
        return e != null && e.frequency == frequency;
    }
    boolean hasEndpoint(Location location) { return endpoints.containsKey(location); }
    /** Do not refresh UNKNOWN chunk tickets while polling an already-loaded endpoint. */
    static @Nullable BlockEntity loadedBlockEntity(ServerLevel level, BlockPos pos) {
        // getChunkNow only returns full-status chunks, so a null chunk already means "not loaded".
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntity(pos, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
    }
    static @Nullable IEnergyStorage energyInput(ServerLevel level, BlockPos pos, Direction face) {
        return energyInput(level, pos, loadedBlockEntity(level, pos), face);
    }

    /** The sender already validated this loaded instance; capability resolution is still fresh. */
    private static @Nullable IEnergyStorage energyInput(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, Direction face) {
        if (be == null || be.isRemoved() || excludedInput(be)) return null;
        if (be instanceof appeng.api.parts.IPartHost host && excludedInput(host.getPart(face))) return null;
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, be.getBlockState(), be, face);
    }

    private static boolean excludedInput(@Nullable Object host) {
        return host instanceof appeng.helpers.InterfaceLogicHost
                || host instanceof appeng.helpers.patternprovider.PatternProviderLogicHost;
    }

    /** Per-visit freshness gate on the already-validated BE; only the capability instance itself is cached. */
    private static boolean excludedVisit(@Nullable BlockEntity be, Direction face) {
        return be == null || be.isRemoved() || excludedInput(be)
                || (be instanceof appeng.api.parts.IPartHost host && excludedInput(host.getPart(face)));
    }

    /**
     * Cached FE capability per endpoint. The chunk lookup plus provider-chain
     * iteration of {@code Level#getCapability} runs once per invalidation
     * instead of once per phase per visit; invalidation (block-entity removal,
     * capability rebuild, chunk unload/load) re-resolves on the next call, so a
     * replaced capability never keeps its old identity past the next visit.
     */
    private static @Nullable IEnergyStorage cachedEnergyTarget(Endpoint e, ServerLevel level) {
        var cache = e.capabilityCache;
        if (cache == null || cache.level() != level) {
            cache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, level,
                    e.location.pos(), e.face);
            e.capabilityCache = cache;
        }
        return cache.getCapability();
    }

    /** Select an input without sending or simulating FE; keep the clicked face preferred. */
    public @Nullable Direction findInputFace(ServerLevel level, BlockPos pos, @Nullable Direction preferred) {
        if (preferred != null) {
            var input = energyInput(level, pos, preferred);
            if (input != null && input.canReceive()) return preferred;
        }
        for (var face : Direction.values()) {
            if (face == preferred) continue;
            var input = energyInput(level, pos, face);
            if (input != null && input.canReceive()) return face;
        }
        return null;
    }

    public boolean isPotentialTarget(ServerLevel level, BlockPos pos, Direction face) {
        return findInputFace(level, pos, face) != null;
    }

    /** Queues a server-derived group operation; completion runs on a later server tick. */
    public BulkResult toggleConnected(ServerPlayer player, int frequency, ServerLevel level, BlockPos seed,
                                      Direction preferred, java.util.function.Consumer<BulkResult> completion) {
        return bulkConnections.queue(this, player, frequency, level, seed, preferred, completion);
    }

    public void tickBulkConnections(MinecraftServer server) {
        bulkConnections.tick(this, server);
    }

    /** Idempotent desired state supports a later link-management UI without replaying card toggles. */
    public String setBound(ServerPlayer player, int frequency, ServerLevel level, BlockPos pos, Direction face,
                           boolean bind) {
        return setBound(player, frequency, level, pos, face, bind, "manual");
    }
    String setBound(ServerPlayer player, int frequency, ServerLevel level, BlockPos pos, Direction face,
                    boolean bind, String source) {
        return setBound(player, frequency, level, pos, face, bind, source, true, null);
    }
    String setBound(ServerPlayer player, int frequency, ServerLevel level, BlockPos pos, Direction face,
                    boolean bind, String source, boolean requireNearby, @Nullable BlockEntity expected) {
        if (player.serverLevel() != level || player.isSpectator() || !player.mayBuild()
                || !level.isLoaded(pos) || (requireNearby && !player.canInteractWithBlock(pos, 1))
                || !level.mayInteract(player, pos)
                || !com.moakiee.ae2lt.logic.world.PlayerWorldActions.mayInteract(player, level, pos)) return "no_permission";
        var manager = WirelessFrequencyManager.get(level.getServer());
        var f = manager == null ? null : manager.getFrequency(frequency);
        if (f == null || !f.getPlayerAccess(player.getUUID()).canUse()) return "no_permission";
        if (expected != null && (expected.isRemoved() || loadedBlockEntity(level, pos) != expected)) return "target_changed";
        var location = new Location(level.dimension(), pos);
        var old = endpoints.get(location);
        if (old != null && old.frequency != frequency) return "other_frequency";
        if (old != null && !old.actor.equals(player.getUUID()) && !f.getPlayerAccess(player).isManager()) return "no_permission";
        if (source.equals("manual")) WirelessEnergyAdmissions.get(level.getServer()).manualOverride(location, player.getUUID(), frequency, face);
        if (!bind) { remove(location); return "disconnected"; }
        BlockEntity be = loadedBlockEntity(level, pos);
        var capability = energyInput(level, pos, face);
        if (be == null || capability == null || !capability.canReceive()) return "unsupported";
        if (be.isRemoved() || loadedBlockEntity(level, pos) != be) return "target_changed";
        if (old != null) return "connected";
        if (!AppFluxBridge.canUseEnergyHandler() || AppFluxBridge.FE_KEY == null) return "unavailable";
        var controller = controller(level.getServer(), frequency);
        if (controller == null) return "no_controller";
        if (endpoints.size() >= WORLD_LIMIT) return "limit";
        if (isBackfeed(be, controller.getMainNode().getGrid())) return "backfeed";
        var endpoint = new Endpoint(UUID.randomUUID(), frequency, location, face, player.getUUID(),
                BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString(),
                BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString());
        endpoint.bindIdentity(be);
        endpoint.source = source;
        endpoints.put(location, endpoint);
        scheduleDirty = true;
        setDirty();
        // Manual admission deliberately calls neither simulateReceive nor receiveEnergy.
        return "connected";
    }

    /** Automatic admission keeps its small, one-shot probe; normal output follows target demand. */
    long sendProbe(ServerPlayer player, int frequency, ServerLevel level, BlockPos pos, Direction face, TargetAccess target,
                   java.util.function.BooleanSupplier current) {
        if (!current.getAsBoolean() || !AppFluxBridge.canUseEnergyHandler() || AppFluxBridge.FE_KEY == null) return 0;
        var c = controller(level.getServer(), frequency);
        if (c == null || !c.getMainNode().isActive()) return 0;
        var node = c.getMainNode().getNode();
        var grid = c.getMainNode().getGrid();
        var be = loadedBlockEntity(level, pos);
        if (node == null || grid == null || be == null || isBackfeed(be, grid)) return 0;
        var service = grid.getService(WirelessEnergyGridService.class);
        if (service.topologyPaused(level.getServer().getTickCount())) return 0;
        long limit = WirelessEnergyAdmissions.PROBE_LIMIT;
        long demand = Math.min(limit, Math.max(0, target.simulateReceive(limit)));
        if (demand <= 0 || !current.getAsBoolean()) return 0;
        var inventory = new GuardedEnergyStorage(grid.getStorageService().getInventory(),
                () -> c.getMainNode().getGrid() == grid && !c.isRemoved());
        var source = IActionSource.ofMachine(c);
        var escrow = service.escrow(node);
        long available = inventory.extract(AppFluxBridge.FE_KEY, demand + WirelessEnergySettings.reserveFe.get(), Actionable.SIMULATE, source);
        long fromNetwork = Math.max(0, available - WirelessEnergySettings.reserveFe.get());
        demand = Math.min(demand, Math.min(Long.MAX_VALUE - fromNetwork, escrow.available()) + fromNetwork);
        if (demand <= 0) return 0;
        var buffer = new BufferedMEStorage(inventory, null, null, escrow);
        try {
            long pulled = buffer.beginMemoryBatch(AppFluxBridge.FE_KEY, demand, source);
            if (pulled <= 0 || !current.getAsBoolean() || c.getMainNode().getGrid() != grid || c.getFrequencyId() != frequency) return 0;
            long accepted = AppFluxBridge.sendToTargetKnownDemand(target, buffer, source, pulled);
            com.moakiee.ae2lt.logic.management.PowerTelemetry.record(node,
                    com.moakiee.ae2lt.logic.management.PowerTelemetry.Kind.WIRELESS,
                    new com.moakiee.ae2lt.logic.management.PowerTelemetry.Target(level.dimension().location().toString(), pos, face.ordinal()),
                    null, accepted);
            return accepted;
        } finally {
            if (c.getMainNode().getGrid() == grid) buffer.endBatch(AppFluxBridge.FE_KEY, source);
        }
    }
    @Nullable private static AdvancedWirelessOverloadedControllerBlockEntity controller(MinecraftServer server, int f) {
        var manager = WirelessFrequencyManager.get(server);
        var t = manager == null ? null : manager.findTransmitter(f);
        if (t == null || !t.advanced()) return null;
        var level = server.getLevel(t.dimension());
        if (level == null || !level.isLoaded(t.pos())) return null;
        return loadedBlockEntity(level, t.pos()) instanceof AdvancedWirelessOverloadedControllerBlockEntity c
                && c.getFrequencyId() == f && c.isFrequencyActive() ? c : null;
    }
    /** Backfeed gateways identified by registry singleton instead of per-visit key-string building. */
    private static final class BackfeedTypes {
        static final BlockEntityType<?> FLUX_ACCESSOR = resolve("appflux:flux_accessor");
        static final BlockEntityType<?> ENERGY_ACCEPTOR = resolve("ae2:energy_acceptor");
        private static BlockEntityType<?> resolve(String id) {
            var key = ResourceLocation.tryParse(id);
            return key == null ? null : BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(key).orElse(null);
        }
    }
    private static boolean isBackfeed(BlockEntity be, @Nullable IGrid grid) {
        // Reject known network power/storage gateways only. Independent FE machines on the same grid remain valid.
        var type = be.getType();
        if (type != BackfeedTypes.FLUX_ACCESSOR && type != BackfeedTypes.ENERGY_ACCEPTOR
                && !(be instanceof com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity)) return false;
        var host = GridHelper.getNodeHost(be.getLevel(), be.getBlockPos());
        var node = host == null ? null : host.getGridNode(Direction.UP);
        return node != null && node.getGrid() == grid;
    }
    /** Small ME batches share one global time budget, including demand simulation and delivery. */
    public void tick(MinecraftServer server) {
        if (ticking || !AppFluxBridge.canUseEnergyHandler() || AppFluxBridge.FE_KEY == null) return;
        long now = server.getTickCount();
        if (lastTick == now && !scheduleDirty) return;
        lastTick = now;
        ticking = true;
        var work = new WirelessEnergyWorkBudget(WirelessEnergySettings.tickBudgetMillis.get());
        try {
            if (scheduleDirty) {
                schedule.rebuild(endpoints.values(), now); scheduleDirty = false;
            }
            schedule.advance(now);
            int remaining = schedule.readyCount();
            var visited = visitScratch;
            visited.clear();
            while (remaining > 0 && (visited.isEmpty() || work.hasTime())) {
                int count = Math.min(BATCH_SIZE, remaining);
                for (int i = 0; i < count && (i == 0 || work.hasTime()); i++) {
                    Endpoint e = schedule.remove();
                    remaining--;
                    if (!e.registered || e.state == State.FAULTED) continue;
                    e.lastVisitedTick = now;
                    e.lastDelivered = 0;
                    e.state = State.BLOCKED;
                    visited.add(e);
                    try { planVisit(server, e); }
                    catch (RuntimeException ex) { fault(e, ex); }
                }
                dispatchBatches(server, work);
                for (var ctx : batches) { ctx.pending.clear(); ctx.totalDemand = 0; }
                batches.clear();
                clearPending();
            }
        } finally {
            // Deferred deliveries precede serviced endpoints next tick. The ready
            // backlog already precedes both, so slow grids cannot starve later grids.
            for (var e : visitScratch) if (e.state == State.WAITING) requeue(e, now);
            for (var e : visitScratch) if (e.state != State.WAITING) requeue(e, now);
            // Release captured world/capability references even when callbacks fail.
            contexts.clear();
            batches.clear();
            visitScratch.clear();
            clearPending();
            ticking = false;
        }
    }

    private void clearPending() {
        for (int i = 0; i < pendingCount; i++) pendingScratch[i].clear();
        pendingCount = 0;
    }

    private void requeue(Endpoint e, long now) {
        if (!e.registered || e.state == State.FAULTED) return;
        e.scheduleDelay = e.state == State.ACTIVE || e.state == State.WAITING
                ? 1 : Math.min(WirelessEnergySchedule.MAX_DELAY, e.scheduleDelay + 1);
        e.nextVisitTick = now + e.scheduleDelay;
        schedule.schedule(e);
    }

    private void fault(Endpoint e, RuntimeException ex) {
        e.state = State.FAULTED;
        e.telemetryMeter = null;
        e.telemetryMeterOwner = null;
        setDirty();
        com.mojang.logging.LogUtils.getLogger().warn("Wireless FE endpoint {} failed; ambiguous delivery is quarantined", e.id, ex);
    }

    @Nullable
    private DispatchContext contextFor(MinecraftServer server, int frequency) {
        var cached = contexts.get(frequency);
        if (cached != null) {
            if (cached.valid()) return cached;
            contexts.remove(frequency);
        }
        var c = controller(server, frequency);
        if (c == null || !c.getMainNode().isActive()) return null;
        var node = c.getMainNode().getNode();
        var grid = c.getMainNode().getGrid();
        if (node == null || grid == null) return null;
        var service = grid.getService(WirelessEnergyGridService.class);
        var inventory = grid.getStorageService().getInventory();
        var guarded = new GuardedEnergyStorage(inventory,
                () -> c.getMainNode().getGrid() == grid && !c.isRemoved());
        var escrow = service.escrow(node);
        var ctx = new DispatchContext(c, frequency, node, grid, service, escrow,
                IActionSource.ofMachine(c), inventory, guarded,
                new BufferedMEStorage(guarded, null, null, escrow), PowerTelemetry.sourceMeters(node));
        contexts.put(frequency, ctx);
        return ctx;
    }

    private void planVisit(MinecraftServer server, Endpoint e) {
        var manager = WirelessFrequencyManager.get(server);
        var f = manager == null ? null : manager.getFrequency(e.frequency);
        if (f == null) { remove(e.location); return; }
        if (!f.getPlayerAccess(e.actor).canUse()) { e.state = State.NO_PERMISSION; return; }
        var level = server.getLevel(e.location.dimension());
        if (level == null) { e.state = State.UNLOADED; return; }
        var pos = e.location.pos();
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) { e.state = State.UNLOADED; return; }
        var be = chunk.getBlockEntity(pos, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
        if (be == null || !e.matches(be)) { remove(e.location); return; }
        var ctx = contextFor(server, e.frequency);
        if (ctx == null) {
            // Drop the meter handle so a dead controller's meter table is not pinned.
            e.state = State.NO_CONTROLLER;
            e.telemetryMeter = null;
            e.telemetryMeterOwner = null;
            return;
        }
        if (isBackfeed(be, ctx.grid)) { e.state = State.BLOCKED; return; }
        if (ctx.service.topologyPaused(server.getTickCount())) { e.state = State.WAITING; return; }
        // Per-visit freshness gates stay on the validated BE; the capability
        // instance itself comes from the endpoint's invalidation-backed cache.
        if (excludedVisit(be, e.face)) { e.state = State.BLOCKED; return; }
        IEnergyStorage target = cachedEnergyTarget(e, level);
        if (target == null || !target.canReceive()) { e.state = State.BLOCKED; return; }
        long demand;
        e.access.target = target;
        try { demand = WirelessEnergyDistributor.simulateNormalDemand(e.access); }
        finally { e.access.target = null; }
        if (demand == 0 || be.isRemoved() || !e.registered
                || ctx.controller.getMainNode().getGrid() != ctx.grid) { e.state = State.BLOCKED; return; }
        // Allocate fixed identity before dispatch allocates the larger histories.
        if (e.telemetryKey == null) e.telemetry();
        if (ctx.pending.isEmpty()) batches.add(ctx);
        var pending = pendingScratch[pendingCount++];
        pending.prepare(e, f, level, be, target, demand);
        ctx.pending.add(pending);
        e.state = State.WAITING;
        ctx.totalDemand = saturatingAdd(ctx.totalDemand, demand);
    }

    private void dispatchBatches(MinecraftServer server, WirelessEnergyWorkBudget work) {
        for (var ctx : batches) {
            if (!work.canDispatch()) break;
            try { dispatchBatch(server, ctx, work); }
            catch (RuntimeException ex) { faultContext(ctx, ex); }
        }
    }

    private void dispatchBatch(MinecraftServer server, DispatchContext ctx, WirelessEnergyWorkBudget work) {
        var feKey = AppFluxBridge.FE_KEY;
        long reserve = saturatingAdd(WirelessEnergySettings.reserveFe.get(), ctx.service.maintenanceReserveFe());
        long available = ctx.guarded.extract(feKey, saturatingAdd(ctx.totalDemand, reserve),
                Actionable.SIMULATE, ctx.source);
        long fromNetwork = Math.max(0, available - reserve);
        long budget = Math.min(Long.MAX_VALUE - fromNetwork, ctx.escrow.available()) + fromNetwork;
        long requested = Math.min(ctx.totalDemand, budget);
        try {
            var manager = WirelessFrequencyManager.get(server);
            WirelessEnergyDistributor.dispatchNormalBatch(ctx.buffer, ctx.source, requested,
                    new WirelessEnergyDistributor.NormalBatch() {
                @Override public int size() { return ctx.pending.size(); }
                @Override public long demand(int i) { return ctx.pending.get(i).demand; }
                @Override public boolean canContinue() { return work.canDispatch(); }
                @Override public TargetAccess target(int i) {
                    work.dispatched();
                    return resolvePending(manager, ctx, ctx.pending.get(i));
                }
                @Override public void noEnergy(int i) { ctx.pending.get(i).endpoint.state = State.NO_FE; }
                @Override public void transferred(int i, long delivered) {
                    var e = ctx.pending.get(i).endpoint;
                    e.access.target = null;
                    recordDelivered(ctx, e, delivered);
                }
                @Override public void failed(int i, RuntimeException failure) {
                    var e = ctx.pending.get(i).endpoint;
                    e.access.target = null;
                    fault(e, failure);
                }
            });
        } finally {
            // Never refund through an inventory captured from an obsolete grid.
            if (ctx.controller.getMainNode().getGrid() == ctx.grid) {
                ctx.buffer.endBatch(feKey, ctx.source);
            }
        }
    }

    /** The shared NORMAL sender asks the host to validate its prepared target before each receipt. */
    private @Nullable TargetAccess resolvePending(WirelessFrequencyManager manager, DispatchContext ctx, Pending p) {
        var e = p.endpoint;
        // ME callbacks may replace/unload a target or move the controller into a new grid.
        if (p.blockEntity.isRemoved() || !e.registered
                || ctx.controller.getMainNode().getGrid() != ctx.grid
                || !p.level.isLoaded(e.location.pos())
                || manager == null || manager.getFrequency(e.frequency) != p.frequency
                || !p.frequency.getPlayerAccess(e.actor).canUse()
                || ctx.controller.getFrequencyId() != e.frequency) {
            return null;
        }
        if (excludedVisit(p.blockEntity, e.face)) return null;
        var fresh = cachedEnergyTarget(e, p.level);
        if (fresh != p.target) return null;
        var access = e.access;
        access.target = p.target;
        return access;
    }

    private void recordDelivered(DispatchContext ctx, Endpoint e, long delivered) {
        if (delivered > 0) {
            // Equivalent to a keyed meter lookup: the handle is dropped exactly
            // when the meter table's own 1200-tick expiry would evict it.
            long now = ctx.node.getLevel().getServer().getTickCount();
            var meter = e.telemetryMeter;
            if (!PowerTelemetry.live(meter, now) || e.telemetryMeterOwner != ctx.telemetryMeters) {
                e.telemetryMeter = meter = PowerTelemetry.meterFor(ctx.telemetryMeters, e.telemetry(), now);
                e.telemetryMeterOwner = ctx.telemetryMeters;
            }
            PowerTelemetry.record(meter, delivered, now);
        }
        e.lastDelivered = delivered;
        e.totalDelivered = Math.min(Long.MAX_VALUE - delivered, e.totalDelivered) + delivered;
        e.state = delivered > 0 ? State.ACTIVE : State.BLOCKED;
        if (delivered > 0) setDirty();
    }

    private void faultContext(DispatchContext ctx, RuntimeException ex) {
        for (var p : ctx.pending) fault(p.endpoint, ex);
    }

    private static long saturatingAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0L ? Long.MAX_VALUE : r;
    }
    static WirelessEnergyEndpoints load(CompoundTag root, HolderLookup.Provider registries) {
        var data = new WirelessEnergyEndpoints();
        var list = root.getList("endpoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var row = list.getCompound(i);
            var dimension = ResourceLocation.tryParse(row.getString("dimension"));
            var face = Direction.byName(row.getString("face"));
            if (dimension == null || face == null || !row.hasUUID("id") || !row.hasUUID("actor")) continue;
            var location = new Location(ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(row.getLong("pos")));
            var e = new Endpoint(row.getUUID("id"), row.getInt("frequency"), location, face, row.getUUID("actor"),
                    row.getString("block"), row.getString("type"));
            e.totalDelivered = Math.max(0, row.getLong("delivered"));
            e.source = row.getString("source").equals("automatic") ? "automatic" : "manual";
            if (row.getBoolean("faulted")) e.state = State.FAULTED;
            data.endpoints.putIfAbsent(location, e);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var e : endpoints.values()) {
            var row = new CompoundTag();
            row.putUUID("id", e.id); row.putInt("frequency", e.frequency); row.putUUID("actor", e.actor);
            row.putString("dimension", e.location.dimension().location().toString()); row.putLong("pos", e.location.pos().asLong());
            row.putString("face", e.face.getName()); row.putString("block", e.block); row.putString("type", e.type);
            row.putString("source", e.source); row.putLong("delivered", e.totalDelivered); row.putBoolean("faulted", e.state == State.FAULTED);
            list.add(row);
        }
        root.put("endpoints", list);
        return root;
    }
}
