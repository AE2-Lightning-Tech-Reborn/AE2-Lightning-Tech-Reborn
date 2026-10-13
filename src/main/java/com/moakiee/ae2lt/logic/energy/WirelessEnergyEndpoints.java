package com.moakiee.ae2lt.logic.energy;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import java.util.ArrayList;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/** FE endpoints never create grid nodes/connections. All access is on the server thread. */
public final class WirelessEnergyEndpoints extends SavedData {
    private static final int WORLD_LIMIT = 8192;
    public record Location(ResourceKey<Level> dimension, BlockPos pos) {
        public Location { pos = pos.immutable(); }
    }
    public enum State { WAITING, UNLOADED, NO_CONTROLLER, NO_PERMISSION, NO_FE, BLOCKED, ACTIVE, FAULTED }
    public record View(UUID id, int frequency, Location location, Direction face, UUID actor,
                       State state, long lastDelivered, long totalDelivered) {}
    private static final class Endpoint {
        final UUID id;
        final int frequency;
        final Location location;
        final Direction face;
        final UUID actor;
        final String block, type;
        State state = State.WAITING;
        long lastDelivered, totalDelivered;
        Endpoint(UUID id, int frequency, Location location, Direction face, UUID actor, String block, String type) {
            this.id = id; this.frequency = frequency; this.location = location; this.face = face;
            this.actor = actor; this.block = block; this.type = type;
        }
        View view() { return new View(id, frequency, location, face, actor, state, lastDelivered, totalDelivered); }
    }
    private final Map<Location, Endpoint> endpoints = new LinkedHashMap<>();
    private List<Endpoint> schedule = List.of();
    private boolean scheduleDirty = true;
    private int cursor;
    private boolean ticking;
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
    }
    private void remove(Location location) {
        if (endpoints.remove(location) != null) { scheduleDirty = true; setDirty(); }
    }
    public List<View> inspect(int frequency, UUID viewer) {
        var manager = WirelessFrequencyManager.get();
        var f = manager == null ? null : manager.getFrequency(frequency);
        if (f == null || !f.getPlayerAccess(viewer).canUse()) return List.of();
        return endpoints.values().stream().filter(e -> e.frequency == frequency).map(Endpoint::view).toList();
    }
    public boolean isBound(ServerLevel level, BlockPos pos, int frequency) {
        var e = endpoints.get(new Location(level.dimension(), pos));
        return e != null && e.frequency == frequency;
    }
    public boolean isPotentialTarget(ServerLevel level, BlockPos pos, Direction face) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) != null
                && level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, face) != null;
    }

    /** Idempotent desired state supports a later link-management UI without replaying card toggles. */
    public String setBound(ServerPlayer player, int frequency, ServerLevel level, BlockPos pos, Direction face,
                           boolean bind) {
        if (player.serverLevel() != level || player.isSpectator() || !player.mayBuild()
                || !level.isLoaded(pos) || !player.canInteractWithBlock(pos, 1)
                || !level.mayInteract(player, pos)) return "no_permission";
        var manager = WirelessFrequencyManager.get(level.getServer());
        var f = manager == null ? null : manager.getFrequency(frequency);
        if (f == null || !f.getPlayerAccess(player.getUUID()).canUse()) return "no_permission";
        var location = new Location(level.dimension(), pos);
        var old = endpoints.get(location);
        if (old != null && old.frequency != frequency) return "other_frequency";
        if (old != null && !old.actor.equals(player.getUUID()) && !f.getPlayerAccess(player).isManager()) return "no_permission";
        if (!bind) { remove(location); return "disconnected"; }
        if (old != null) return "connected";
        if (!AppFluxBridge.canUseEnergyHandler() || AppFluxBridge.FE_KEY == null) return "unavailable";
        if (controller(level.getServer(), frequency) == null) return "no_controller";
        if (endpoints.size() >= WORLD_LIMIT || endpoints.values().stream()
                .filter(e -> e.frequency == frequency).count() >= WirelessEnergySettings.endpointLimit.get()) return "limit";
        BlockEntity be = level.getBlockEntity(pos);
        var capability = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, face);
        if (be == null || capability == null || !capability.canReceive()) return "unsupported";
        var controller = controller(level.getServer(), frequency);
        if (controller == null || isBackfeed(be, controller.getMainNode().getGrid())) return "backfeed";
        endpoints.put(location, new Endpoint(UUID.randomUUID(), frequency, location, face, player.getUUID(),
                BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString(),
                BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString()));
        scheduleDirty = true;
        setDirty();
        // Manual admission deliberately calls neither simulateReceive nor receiveEnergy.
        return "connected";
    }

    @Nullable private static AdvancedWirelessOverloadedControllerBlockEntity controller(MinecraftServer server, int f) {
        var manager = WirelessFrequencyManager.get(server);
        var t = manager == null ? null : manager.findTransmitter(f);
        if (t == null || !t.advanced()) return null;
        var level = server.getLevel(t.dimension());
        if (level == null || !level.isLoaded(t.pos())) return null;
        return level.getBlockEntity(t.pos()) instanceof AdvancedWirelessOverloadedControllerBlockEntity c
                && c.getFrequencyId() == f && c.isFrequencyActive() ? c : null;
    }
    private static boolean isBackfeed(BlockEntity be, @Nullable IGrid grid) {
        // Reject known network power/storage gateways only. Independent FE machines on the same grid remain valid.
        String id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString();
        if (!(id.equals("appflux:flux_accessor") || id.equals("ae2:energy_acceptor")
                || be instanceof com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity)) return false;
        var host = GridHelper.getNodeHost(be.getLevel(), be.getBlockPos());
        var node = host == null ? null : host.getGridNode(Direction.UP);
        return node != null && node.getGrid() == grid;
    }
    public void tick(MinecraftServer server) {
        if (ticking || !AppFluxBridge.canUseEnergyHandler() || AppFluxBridge.FE_KEY == null) return;
        ticking = true;
        try {
            if (scheduleDirty) { schedule = new ArrayList<>(endpoints.values()); scheduleDirty = false; }
            int visits = Math.min(schedule.size(), WirelessEnergySettings.globalVisits.get());
            for (int i = 0; i < visits; i++) {
                if (cursor >= schedule.size()) cursor = 0;
                Endpoint e = schedule.get(cursor++);
                if (endpoints.get(e.location) != e) continue;
                e.lastDelivered = 0;
                try { tickEndpoint(server, e); }
                catch (RuntimeException ex) {
                    e.state = State.FAULTED;
                    setDirty();
                    com.mojang.logging.LogUtils.getLogger().warn("Wireless FE endpoint {} failed; ambiguous delivery is quarantined", e.id, ex);
                }
            }
        } finally { ticking = false; }
    }
    private void tickEndpoint(MinecraftServer server, Endpoint e) {
        if (e.state == State.FAULTED) return; // explicit unbind/rebind required after unknown receipt
        var manager = WirelessFrequencyManager.get(server);
        var f = manager == null ? null : manager.getFrequency(e.frequency);
        if (f == null) { remove(e.location); return; }
        if (!f.getPlayerAccess(e.actor).canUse()) { e.state = State.NO_PERMISSION; return; }
        var level = server.getLevel(e.location.dimension());
        if (level == null || !level.isLoaded(e.location.pos())) { e.state = State.UNLOADED; return; }
        var be = level.getBlockEntity(e.location.pos());
        if (be == null || !e.block.equals(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString())
                || !e.type.equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString())) {
            remove(e.location); return;
        }
        var c = controller(server, e.frequency);
        if (c == null || !c.getMainNode().isActive()) { e.state = State.NO_CONTROLLER; return; }
        var node = c.getMainNode().getNode();
        var grid = c.getMainNode().getGrid();
        if (node == null || grid == null || isBackfeed(be, grid)) { e.state = State.BLOCKED; return; }
        var service = grid.getService(WirelessEnergyGridService.class);
        long limit = service.reserve(server.getTickCount(), WirelessEnergySettings.targetFe.get(),
                WirelessEnergySettings.networkFe.get(), WirelessEnergySettings.networkTargets.get());
        if (limit <= 0) { e.state = State.BLOCKED; return; }
        // Resolve on each bounded visit, so invalidated or unloaded capabilities are never retained.
        IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, e.location.pos(), e.face);
        if (target == null || !target.canReceive()) { e.state = State.BLOCKED; return; }
        long demand = Math.min(limit, Math.max(0, target.receiveEnergy((int) limit, true)));
        if (demand == 0 || be.isRemoved() || endpoints.get(e.location) != e) { e.state = State.BLOCKED; return; }
        var inventory = new GuardedEnergyStorage(grid.getStorageService().getInventory(),
                () -> c.getMainNode().getGrid() == grid && !c.isRemoved());
        var source = IActionSource.ofMachine(c);
        var escrow = service.escrow(node);
        long available = inventory.extract(AppFluxBridge.FE_KEY, demand + WirelessEnergySettings.reserveFe.get(), Actionable.SIMULATE, source);
        long fromNetwork = Math.max(0, available - WirelessEnergySettings.reserveFe.get());
        demand = Math.min(demand, Math.min(Long.MAX_VALUE - fromNetwork, escrow.available()) + fromNetwork);
        if (demand <= 0) { e.state = State.NO_FE; return; }
        var buffer = new BufferedMEStorage(inventory, null, null, escrow);
        try {
            long pulled = buffer.beginMemoryBatch(AppFluxBridge.FE_KEY, demand, source);
            if (pulled <= 0) { e.state = State.NO_FE; return; }
            // ME callbacks may replace/unload a target or move the controller into a new grid.
            if (be.isRemoved() || endpoints.get(e.location) != e || c.getMainNode().getGrid() != grid
                    || !level.isLoaded(e.location.pos()) || !f.getPlayerAccess(e.actor).canUse()) return;
            var fresh = level.getCapability(Capabilities.EnergyStorage.BLOCK, e.location.pos(), e.face);
            if (fresh != target) return;
            e.lastDelivered = AppFluxBridge.sendToTargetKnownDemand(new TargetAccess() {
                public long simulateReceive(long max) { return target.receiveEnergy((int) Math.min(Integer.MAX_VALUE, max), true); }
                public long receive(long amount) { return target.receiveEnergy((int) amount, false); }
            }, buffer, source, pulled);
            e.totalDelivered = Math.min(Long.MAX_VALUE - e.lastDelivered, e.totalDelivered) + e.lastDelivered;
            e.state = e.lastDelivered > 0 ? State.ACTIVE : State.BLOCKED;
            if (e.lastDelivered > 0) setDirty();
        } finally {
            // Never refund through an inventory captured from an obsolete grid.
            if (c.getMainNode().getGrid() == grid) buffer.endBatch(AppFluxBridge.FE_KEY, source);
        }
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
            row.putString("source", "manual"); row.putLong("delivered", e.totalDelivered); row.putBoolean("faulted", e.state == State.FAULTED);
            list.add(row);
        }
        root.put("endpoints", list);
        return root;
    }
}
