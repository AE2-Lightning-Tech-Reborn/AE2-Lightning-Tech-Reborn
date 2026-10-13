package com.moakiee.ae2lt.workstation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;

/** Server-thread lifecycle. Does not call PlayerList.placeNewPlayer/remove/save. */
public final class PigmeeActorManager {
    private final MinecraftServer server;
    private final PigmeeControlLeases controls = new PigmeeControlLeases(this);
    public PigmeeControlLeases controls() { return controls; }
    final PigmeeActorStore store;
    private final Path savePath;
    private final Path runningMarker;
    private boolean markedRunning;
    private CompoundTag cleanSnapshot;

    PigmeeActorManager(MinecraftServer server) {
        this.server = server;
        Path data = server.getWorldPath(LevelResource.ROOT).resolve("data");
        savePath = data.resolve(PigmeeActorStore.KEY + ".dat");
        runningMarker = data.resolve(PigmeeActorStore.KEY + ".running");
        var storage = server.overworld().getDataStorage();
        var loaded = storage.get(PigmeeActorStore.FACTORY, PigmeeActorStore.KEY);
        if (loaded == null && Files.exists(savePath)) {
            throw new IllegalStateException("Unreadable Pigmee save; refusing to overwrite " + savePath);
        }
        store = loaded == null ? new PigmeeActorStore() : loaded;
        if (loaded == null) storage.set(PigmeeActorStore.KEY, store);
        if (Files.exists(runningMarker)) {
            // Includes dormant records: one could have been activated since the last disk save.
            for (var record : store.records.values()) {
                if (record.state != PigmeeActorStore.State.DEAD) {
                    record.state = PigmeeActorStore.State.QUARANTINED;
                    record.diagnostic = "Previous process did not complete a verified clean save";
                }
            }
            store.setDirty();
        }
    }

    private void checkThread() {
        if (!server.isSameThread()) throw new IllegalStateException("Pigmee lifecycle requires the server thread");
        if (!store.writable()) throw new IllegalStateException("Pigmee save quarantined: unsupported or corrupt data");
    }

    public PigmeeServerPlayer create(UUID owner, ServerLevel level, Vec3 pos, float yaw) {
        return activate(prepare(owner, level, pos, yaw, PigmeeVariant.NORMAL).id);
    }

    PigmeeActorStore.Record prepare(UUID owner, ServerLevel level, Vec3 pos, float yaw, PigmeeVariant variant) {
        checkThread();
        if (store.records.size() >= 8) throw new IllegalStateException("P0 limit: eight identities per test world");
        markRunning();
        UUID id;
        do { id = UUID.randomUUID(); } while (id.equals(owner) || store.records.containsKey(id));
        PigmeeServerPlayer actor = new PigmeeServerPlayer(level, id, owner, variant);
        actor.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        actor.setGameMode(variant.gameType());
        if (actor.gameMode.getGameModeForPlayer() != variant.gameType()) {
            actor.getAdvancements().stopListening();
            actor.getTextFilter().leave();
            throw new IllegalStateException("Game mode change denied by native hook");
        }
        var record = new PigmeeActorStore.Record(id, owner, variant, PigmeeActorStore.State.DORMANT,
                actor.saveWithoutId(new CompoundTag()));
        var principal = server.getPlayerList().getPlayer(owner);
        record.ownerName = principal == null ? owner.toString() : principal.getGameProfile().getName();
        store.records.put(id, record);
        actor.getAdvancements().stopListening();
        actor.getTextFilter().leave();
        store.setDirty();
        return record;
    }

    public PigmeeServerPlayer activate(UUID id) {
        checkThread();
        markRunning();
        PigmeeServerPlayer existing = store.live.get(id);
        if (existing != null) return existing;
        var record = require(id);
        if (record.state == PigmeeActorStore.State.DEAD || record.state == PigmeeActorStore.State.QUARANTINED) {
            throw new IllegalStateException("Cannot activate " + record.state + ": " + record.diagnostic);
        }
        for (ServerLevel world : server.getAllLevels()) {
            if (world.getEntity(id) != null) throw new IllegalStateException("UUID already exists in world");
        }
        if (server.getPlayerList().getPlayer(id) != null) throw new IllegalStateException("UUID in login list");
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(record.playerData.getString("Dimension"))));
        if (level == null) throw new IllegalStateException("Saved dimension unavailable");
        if (record.origin != null) {
            ServerLevel originLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(record.origin.getString("Dimension"))));
            if (originLevel == null || PigmeeVariant.fromBlock(originLevel.getBlockState(
                    BlockPos.of(record.origin.getLong("Pos")))) != null) {
                record.state = PigmeeActorStore.State.QUARANTINED;
                record.diagnostic = "Activation source still present or dimension missing; inspect before recovery";
                store.setDirty();
                throw new IllegalStateException(record.diagnostic);
            }
        }
        PigmeeServerPlayer actor = new PigmeeServerPlayer(level, id, record.owner, record.variant);
        actor.load(record.playerData.copy());
        if (!actor.isAlive()) throw new IllegalStateException("Saved actor is dead");
        actor.setGameMode(record.variant.gameType());
        if (actor.gameMode.getGameModeForPlayer() != record.variant.gameType()) {
            actor.getAdvancements().stopListening();
            actor.getTextFilter().leave();
            throw new IllegalStateException("Game mode change denied by native hook");
        }
        actor.setDeltaMovement(Vec3.ZERO);
        actor.initInventoryMenu();
        try {
            sendProfile(actor);
            level.addNewPlayer(actor); // Tracking installs native DistanceManager player tickets.
            if (level.getEntity(id) != actor) throw new IllegalStateException("EntityJoinLevelEvent denied actor");
        } catch (RuntimeException | Error failed) {
            actor.detach();
            record.state = PigmeeActorStore.State.QUARANTINED;
            record.diagnostic = "World admission failed: " + failed.getMessage();
            store.setDirty();
            server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(id)));
            throw failed;
        }
        store.live.put(id, actor);
        record.state = PigmeeActorStore.State.ACTIVE;
        store.cleanShutdown = false;
        store.setDirty();
        return actor;
    }

    public void deactivate(UUID id) {
        checkThread();
        var record = require(id);
        if (record.state != PigmeeActorStore.State.ACTIVE) return;
        var actor = store.live.get(id);
        if (actor != null && actor.deathSettled()) settleDeath(actor);
        else detach(record, PigmeeActorStore.State.DORMANT);
    }

    private void detach(PigmeeActorStore.Record record, PigmeeActorStore.State state) {
        controls.releaseActor(record.id);
        PigmeeServerPlayer actor = store.live.remove(record.id);
        if (actor != null) {
            actor.closeContainer();
            store.snapshot(record, actor);
            actor.detach(); // Native tracking removal releases only this body's loading source.
            server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(record.id)));
            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(new PigmeeIdentityPacket(record.id, -1));
        }
        record.state = state;
        store.setDirty();
    }

    void restore() {
        if (!store.writable()) return;
        for (var record : new ArrayList<>(store.records.values())) {
            if (record.state == PigmeeActorStore.State.ACTIVE) {
                try { activate(record.id); }
                catch (RuntimeException failed) {
                    record.state = PigmeeActorStore.State.QUARANTINED;
                    record.diagnostic = failed.getMessage();
                    store.setDirty();
                }
            }
        }
    }

    void afterTick() {
        controls.tick();
        for (var actor : new ArrayList<>(store.live.values())) {
            if (actor.deathSettled()) settleDeath(actor);
            else if (actor.isRemoved()) {
                var record = require(actor.getUUID());
                record.diagnostic = "Unexpected removal; manual inspection required";
                detach(record, PigmeeActorStore.State.QUARANTINED);
            }
        }
    }

    private void settleDeath(PigmeeServerPlayer actor) {
        var record = require(actor.getUUID());
        boolean first = record.death == null;
        if (first) {
            var owner = server.getPlayerList().getPlayer(record.owner);
            if (owner != null) record.ownerName = owner.getGameProfile().getName();
            record.death = actor.confirmedDeath(record.ownerName.isEmpty() ? record.owner.toString() : record.ownerName);
        }
        // Tombstone and release before notification; a send failure cannot repeat native drops.
        detach(record, PigmeeActorStore.State.DEAD);
        if (first && !Boolean.getBoolean("ae2lt.pigmeeDeathBroadcastDisabled")) {
            server.getPlayerList().broadcastSystemMessage(record.death.message(), false);
        }
    }

    void shutdown() {
        for (var record : new ArrayList<>(store.records.values())) {
            var actor = store.live.get(record.id);
            if (actor != null) {
                if (actor.deathSettled()) settleDeath(actor);
                else detach(record, PigmeeActorStore.State.ACTIVE);
            }
        }
        store.cleanShutdown = true;
        store.setDirty();
        cleanSnapshot = store.save(new CompoundTag(), server.registryAccess());
    }

    private void markRunning() {
        if (markedRunning) return;
        try {
            Files.createDirectories(runningMarker.getParent());
            // Flush a crash guard BEFORE a saved inventory can become an active body.
            IOUtilities.atomicWrite(runningMarker, out -> out.write(new byte[] {1}));
            markedRunning = true;
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot establish Pigmee crash guard", failure);
        }
    }

    void finishedStopping() {
        if (cleanSnapshot == null || !store.writable()) return;
        IOUtilities.waitUntilIOWorkerComplete();
        try {
            // SavedData reports write failures only in logs. Read back before releasing the guard.
            CompoundTag disk = NbtIo.readCompressed(savePath, NbtAccounter.unlimitedHeap());
            if (cleanSnapshot.equals(disk.getCompound("data"))) Files.deleteIfExists(runningMarker);
        } catch (IOException failure) {
            // Keep the guard. Recovery remains quarantined rather than restoring stale inventory.
            com.mojang.logging.LogUtils.getLogger().error("Pigmee clean save could not be verified", failure);
        }
    }

    public PigmeeServerPlayer active(UUID id) { return store.live.get(id); }
    public PigmeeDeathRecord death(UUID id) { return require(id).death; }
    public boolean ownedBy(UUID id, UUID owner) {
        var record = store.records.get(id);
        return record != null && record.owner.equals(owner);
    }
    public int activeCount() { return store.live.size(); }
    public List<UUID> ids() { return List.copyOf(store.records.keySet()); }
    public String status(UUID id) {
        var record = require(id);
        return id + " owner=" + record.owner + " variant=" + record.variant + " state=" + record.state + " " + record.diagnostic;
    }

    private PigmeeActorStore.Record require(UUID id) {
        var record = store.records.get(id);
        if (record == null) throw new IllegalArgumentException("Unknown Pigmee identity " + id);
        return record;
    }

    private void sendProfile(PigmeeServerPlayer actor) {
        server.getPlayerList().broadcastAll(profilePacket(List.of(actor)));
    }

    void informViewer(ServerPlayer viewer) {
        if (!store.live.isEmpty()) viewer.connection.send(profilePacket(List.copyOf(store.live.values())));
    }

    public static ClientboundPlayerInfoUpdatePacket profilePacket(List<PigmeeServerPlayer> actors) {
        // ADD_PLAYER supplies tracking identity; UPDATE_LISTED is deliberately absent.
        return new ClientboundPlayerInfoUpdatePacket(EnumSet.of(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), new ArrayList<ServerPlayer>(actors));
    }
}
