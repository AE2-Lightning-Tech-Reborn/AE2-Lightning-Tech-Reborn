package com.moakiee.ae2lt.logic.energy;

import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/** One automatic admission per physical placement. Terminal records are never retried or evicted. */
public final class WirelessEnergyAdmissions extends SavedData {
    static final int PROBE_LIMIT = 100;
    private static final int WORLD_LIMIT = 8192, VISITS_PER_TICK = 32, TIMEOUT_TICKS = 100;
    public enum State { QUEUED, WAITING, ACCEPTED, NO_STORED_ENERGY, NO_INPUT, UNVERIFIED, NO_PERMISSION, MANUAL, FAILED }
    public record View(UUID generation, State state, long offered, long accepted, int frequency) {}
    private static final class Attempt {
        final UUID generation, actor;
        final WirelessEnergyEndpoints.Location location;
        final int frequency;
        Direction face;
        State state = State.QUEUED;
        long offered, accepted, serial, inputSerial;
        int lastMachineTick = Integer.MIN_VALUE, expires;
        BlockEntity expected;
        IEnergyStorage capability;
        ServerPlayer player;
        Attempt(UUID generation, UUID actor, WirelessEnergyEndpoints.Location location, int frequency, Direction face) {
            this.generation = generation; this.actor = actor; this.location = location; this.frequency = frequency; this.face = face;
        }
        boolean pending() { return state == State.QUEUED || state == State.WAITING; }
        View view() { return new View(generation, state, offered, accepted, frequency); }
    }
    private final Map<WirelessEnergyEndpoints.Location, Attempt> attempts = new LinkedHashMap<>();
    private final ArrayDeque<Attempt> pending = new ArrayDeque<>();
    private final Map<BlockEntity, Attempt> observing = new java.util.IdentityHashMap<>();
    // If bounded history cannot record a manual generation, no later placement can
    // safely be distinguished from that generation. Persistently fail closed.
    private boolean historyIncomplete;
    private boolean ticking;
    private static MinecraftServer owner;
    private static WirelessEnergyAdmissions instance;

    public static WirelessEnergyAdmissions get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Server thread required");
        if (owner != server) {
            owner = server;
            instance = server.overworld().getDataStorage().computeIfAbsent(
                    new Factory<>(WirelessEnergyAdmissions::new, WirelessEnergyAdmissions::load), "ae2lt_wireless_energy_admissions");
        }
        return instance;
    }
    public static void stop() { instance = null; owner = null; }

    /** Event-only entry: capture the placed instance now, never resolve an old placement against a replacement later. */
    public static void placed(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player.isFakePlayer()) return;
        var card = OverloadedFrequencyCardItem.findAutoConnectEnergyCard(player);
        if (card.isEmpty()) return;
        var data = OverloadedFrequencyCardItem.getData(card.get());
        if (data.isBound() && data.canBeUsedBy(player.getUUID())) get(level.getServer()).queue(player, data.frequencyId(), level, pos, null);
    }
    /** Package-visible so the server GameTests can use a fake player without enabling fake-player placement hooks. */
    void queue(ServerPlayer player, int frequency, ServerLevel level, BlockPos pos, @Nullable Direction preferred) {
        var location = new WirelessEnergyEndpoints.Location(level.dimension(), pos);
        if (historyIncomplete || attempts.containsKey(location) || attempts.size() >= WORLD_LIMIT
                || WirelessEnergyEndpoints.get(level.getServer()).hasEndpoint(location)
                || !allowed(player, frequency, level, pos)) return;
        var be = WirelessEnergyEndpoints.loadedBlockEntity(level, pos);
        if (be == null) return;
        Direction selected = null;
        IEnergyStorage target = null;
        // At most six capability reads, no energy IO while choosing the single input face.
        for (var face : preferred == null ? Direction.values() : new Direction[] { preferred }) {
            var cap = WirelessEnergyEndpoints.energyInput(level, pos, face);
            if (cap != null && cap.canReceive()) { selected = face; target = cap; break; }
        }
        // Placement events precede onPlace/neighbour notifications. The same BE
        // may expose or replace its FE capability only after initialization.
        var a = new Attempt(UUID.randomUUID(), player.getUUID(), location, frequency, selected);
        a.expected = be; a.capability = target; a.player = player;
        a.expires = level.getServer().getTickCount() + TIMEOUT_TICKS;
        attempts.put(location, a); pending.addLast(a); observing.put(be, a); setDirty();
    }
    private static boolean allowed(ServerPlayer player, int frequency, ServerLevel level, BlockPos pos) {
        var manager = WirelessFrequencyManager.get(level.getServer());
        var f = manager == null ? null : manager.getFrequency(frequency);
        return player != null && !player.isRemoved() && player.serverLevel() == level && !player.isSpectator()
                && player.mayBuild() && level.isLoaded(pos) && player.canInteractWithBlock(pos, 1)
                && level.mayInteract(player, pos) && f != null && f.getPlayerAccess(player.getUUID()).canUse();
    }
    public @Nullable View inspect(ServerLevel level, BlockPos pos, UUID viewer) {
        var a = attempts.get(new WirelessEnergyEndpoints.Location(level.dimension(), pos));
        if (a == null || a.face == null) return null;
        var manager = WirelessFrequencyManager.get(level.getServer());
        var f = manager == null ? null : manager.getFrequency(a.frequency);
        return f != null && f.getPlayerAccess(viewer).canUse() ? a.view() : null;
    }
    public static void onMachineTick(BlockEntity be) {
        if (instance == null || instance.observing.isEmpty() || !(be.getLevel() instanceof ServerLevel level) || owner != level.getServer()) return;
        var a = instance.observing.get(be);
        if (a != null && a.pending() && a.expected == be && !be.isRemoved()) {
            a.serial++; a.lastMachineTick = owner.getTickCount();
        }
    }
    public static void onUnavailable(BlockEntity be) {
        if (instance == null || instance.observing.isEmpty() || !(be.getLevel() instanceof ServerLevel level) || owner != level.getServer()) return;
        var a = instance.observing.get(be);
        if (a != null && a.pending() && a.expected == be) instance.finish(a, State.UNVERIFIED);
    }
    /** Actual removal ends a generation; plain chunk unload only interrupts its observation. */
    public static void onRemoved(ServerLevel level, BlockPos pos) {
        if (instance != null && owner == level.getServer()) {
            var a = instance.attempts.remove(new WirelessEnergyEndpoints.Location(level.dimension(), pos));
            if (a != null) { instance.finish(a, State.UNVERIFIED); instance.setDirty(); }
        }
    }
    void manualOverride(WirelessEnergyEndpoints.Location location, UUID actor, int frequency, Direction face) {
        var a = attempts.get(location);
        if (a == null && attempts.size() >= WORLD_LIMIT) {
            if (!historyIncomplete) {
                historyIncomplete = true;
                setDirty();
                com.mojang.logging.LogUtils.getLogger().warn("Wireless FE admission history is full; new automatic admissions are disabled to preserve manual-generation exclusions");
            }
            return;
        }
        if (a == null) {
            a = new Attempt(UUID.randomUUID(), actor, location, frequency, face);
            attempts.put(location, a);
        }
        a.face = face;
        finish(a, State.MANUAL);
    }
    private void finish(Attempt a, State state) {
        observing.remove(a.expected);
        a.state = state; a.expected = null; a.capability = null; a.player = null; setDirty();
        if (a.face == null) attempts.remove(a.location, a);
    }
    public void tick(MinecraftServer server) {
        if (ticking) return;
        ticking = true;
        try {
            int visits = Math.min(VISITS_PER_TICK, pending.size());
            for (int i = 0; i < visits; i++) {
                var a = pending.removeFirst();
                if (attempts.get(a.location) != a || !a.pending()) continue;
                try { visit(server, a); }
                catch (RuntimeException e) {
                    finish(a, State.FAILED);
                    com.mojang.logging.LogUtils.getLogger().warn("Automatic FE admission {} failed; no replay allowed", a.generation, e);
                }
                if (a.pending()) pending.addLast(a);
            }
        } finally { ticking = false; }
    }
    private boolean currentPlacement(MinecraftServer server, Attempt a) {
        var level = server.getLevel(a.location.dimension());
        return attempts.get(a.location) == a && a.pending() && level != null
                && allowed(a.player, a.frequency, level, a.location.pos())
                && !a.expected.isRemoved() && WirelessEnergyEndpoints.loadedBlockEntity(level, a.location.pos()) == a.expected
                && !WirelessEnergyEndpoints.get(server).hasEndpoint(a.location);
    }
    private boolean current(MinecraftServer server, Attempt a) {
        var level = server.getLevel(a.location.dimension());
        return currentPlacement(server, a) && a.capability != null
                && WirelessEnergyEndpoints.energyInput(level, a.location.pos(), a.face) == a.capability;
    }
    private void visit(MinecraftServer server, Attempt a) {
        var level = server.getLevel(a.location.dimension());
        if (!currentPlacement(server, a)) { finish(a, State.UNVERIFIED); return; }
        if (server.getTickCount() >= a.expires) {
            finish(a, State.UNVERIFIED);
            return;
        }
        if (a.state == State.WAITING) {
            if (!current(server, a)) { finish(a, State.UNVERIFIED); return; }
            if (a.serial <= a.inputSerial) return;
            var stored = new NativeFeTarget(a.capability).storedEnergyFe();
            if (stored.isEmpty()) { finish(a, State.UNVERIFIED); return; }
            if (stored.getAsLong() == 0) { feedback(a, "auto_no_stored_energy"); finish(a, State.NO_STORED_ENERGY); return; }
            // Called in ServerTick.Post, after the observed dimension's BE tick phase.
            String result = WirelessEnergyEndpoints.get(server).setBound(a.player, a.frequency, level, a.location.pos(), a.face, true, "automatic");
            finish(a, result.equals("connected") ? State.ACCEPTED : State.UNVERIFIED);
            return;
        }
        // Mere chunk loading is insufficient: see an actual ticker execution before sending anything.
        if (a.lastMachineTick != server.getTickCount()) return;
        if (a.face == null) {
            a.face = WirelessEnergyEndpoints.get(server).findInputFace(level, a.location.pos(), null);
            if (a.face == null) return;
            setDirty();
        }
        // Until the one real receive call, initialization and temporary source
        // shortages may be retried. Afterwards capability identity is immutable.
        a.capability = WirelessEnergyEndpoints.energyInput(level, a.location.pos(), a.face);
        if (a.capability == null || !a.capability.canReceive()) return;
        var access = new NativeFeTarget(a.capability);
        if (access.storedEnergyFe().isEmpty()) { feedback(a, "auto_unverifiable"); finish(a, State.UNVERIFIED); return; }
        var receipt = new TargetAccess() {
            @Override public long simulateReceive(long amount) { return access.simulateReceive(amount); }
            @Override public long receive(long amount) {
                if (!current(server, a)) return 0;
                if (a.offered != 0 || amount > PROBE_LIMIT) throw new IllegalStateException("Probe budget exceeded");
                a.state = State.WAITING; a.inputSerial = a.serial; a.offered = amount; setDirty();
                a.accepted = Math.min(amount, access.receive(amount)); setDirty();
                return a.accepted;
            }
        };
        long sent = WirelessEnergyEndpoints.get(server).sendProbe(a.player, a.frequency, level, a.location.pos(), a.face, receipt,
                () -> current(server, a));
        if (sent <= 0 && a.state == State.WAITING) finish(a, State.NO_INPUT);
    }
    private static void feedback(Attempt a, String key) {
        if (a.player != null) a.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("ae2lt.wireless_energy." + key), true);
    }
    static WirelessEnergyAdmissions load(CompoundTag root, HolderLookup.Provider registries) {
        var data = new WirelessEnergyAdmissions();
        data.historyIncomplete = root.getBoolean("historyIncomplete");
        var list = root.getList("attempts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var row = list.getCompound(i);
            var dim = ResourceLocation.tryParse(row.getString("dimension"));
            var face = Direction.byName(row.getString("face"));
            if (dim == null || face == null || !row.hasUUID("generation") || !row.hasUUID("actor")) continue;
            var location = new WirelessEnergyEndpoints.Location(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(row.getLong("pos")));
            var a = new Attempt(row.getUUID("generation"), row.getUUID("actor"), location, row.getInt("frequency"), face);
            String state = row.getString("state");
            try { a.state = state.equals("NO_INCREMENT") ? State.NO_STORED_ENERGY : State.valueOf(state); }
            catch (IllegalArgumentException ignored) { a.state = State.UNVERIFIED; }
            if (a.pending()) a.state = State.UNVERIFIED; // no pre-restart observation is comparable
            a.offered = Math.max(0, Math.min(PROBE_LIMIT, row.getLong("offered")));
            a.accepted = Math.max(0, Math.min(a.offered, row.getLong("accepted")));
            data.attempts.putIfAbsent(location, a);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putBoolean("historyIncomplete", historyIncomplete);
        var list = new ListTag();
        for (var a : attempts.values()) {
            if (a.face == null) continue; // no FE input found and no energy IO performed
            var row = new CompoundTag();
            row.putUUID("generation", a.generation); row.putUUID("actor", a.actor); row.putInt("frequency", a.frequency);
            row.putString("dimension", a.location.dimension().location().toString()); row.putLong("pos", a.location.pos().asLong());
            row.putString("face", a.face.getName()); row.putString("state", a.state.name());
            row.putLong("offered", a.offered); row.putLong("accepted", a.accepted); list.add(row);
        }
        root.put("attempts", list); return root;
    }
}
