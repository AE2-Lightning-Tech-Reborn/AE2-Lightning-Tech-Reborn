package com.moakiee.ae2lt.workstation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Server-thread lifecycle. Does not call PlayerList.placeNewPlayer/remove/save. */
public final class PigmeeActorManager {
    private final MinecraftServer server;
    final PigmeeActorStore store;

    PigmeeActorManager(MinecraftServer server) {
        this.server = server;
        store = server.overworld().getDataStorage().computeIfAbsent(PigmeeActorStore.FACTORY, PigmeeActorStore.KEY);
    }

    private void checkThread() {
        if (!server.isSameThread()) throw new IllegalStateException("Pigmee lifecycle requires the server thread");
        if (!store.writable()) throw new IllegalStateException("Pigmee save quarantined: unsupported or corrupt data");
    }

    public PigmeeServerPlayer create(UUID owner, ServerLevel level, Vec3 pos, float yaw) {
        checkThread();
        if (store.records.size() >= 8) throw new IllegalStateException("P0 limit: eight identities per test world");
        UUID id = UUID.randomUUID();
        PigmeeServerPlayer actor = new PigmeeServerPlayer(level, id, owner);
        actor.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        actor.setGameMode(GameType.SURVIVAL);
        store.records.put(id, new PigmeeActorStore.Record(id, owner, PigmeeActorStore.State.DORMANT,
                actor.saveWithoutId(new CompoundTag())));
        actor.getAdvancements().stopListening();
        actor.getTextFilter().leave();
        return activate(id);
    }

    public PigmeeServerPlayer activate(UUID id) {
        checkThread();
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
        PigmeeServerPlayer actor = new PigmeeServerPlayer(level, id, record.owner);
        actor.load(record.playerData.copy());
        if (!actor.isAlive()) throw new IllegalStateException("Saved actor is dead");
        actor.setGameMode(GameType.SURVIVAL); // P0 only; three variants arrive with P1.
        actor.setDeltaMovement(Vec3.ZERO);
        actor.initInventoryMenu();
        sendProfile(actor);
        level.addNewPlayer(actor); // Entity tracking installs native DistanceManager player tickets.
        if (level.getEntity(id) != actor) {
            actor.getAdvancements().stopListening();
            actor.getTextFilter().leave();
            server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(id)));
            throw new IllegalStateException("EntityJoinLevelEvent denied actor");
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
        detach(record, PigmeeActorStore.State.DORMANT);
    }

    private void detach(PigmeeActorStore.Record record, PigmeeActorStore.State state) {
        PigmeeServerPlayer actor = store.live.remove(record.id);
        if (actor != null) {
            actor.closeContainer();
            store.snapshot(record, actor);
            actor.detach(); // Native tracking removal releases only this body's loading source.
            server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(record.id)));
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
        for (var actor : new ArrayList<>(store.live.values())) {
            if (actor.deathSettled()) detach(require(actor.getUUID()), PigmeeActorStore.State.DEAD);
            else if (actor.isRemoved()) {
                var record = require(actor.getUUID());
                record.diagnostic = "Unexpected removal; manual inspection required";
                detach(record, PigmeeActorStore.State.QUARANTINED);
            }
        }
    }

    void shutdown() {
        for (var record : new ArrayList<>(store.records.values())) {
            if (store.live.containsKey(record.id)) detach(record, PigmeeActorStore.State.ACTIVE);
        }
        store.cleanShutdown = true;
        store.setDirty();
    }

    public PigmeeServerPlayer active(UUID id) { return store.live.get(id); }
    public int activeCount() { return store.live.size(); }
    public List<UUID> ids() { return List.copyOf(store.records.keySet()); }
    public String status(UUID id) {
        var record = require(id);
        return id + " owner=" + record.owner + " state=" + record.state + " " + record.diagnostic;
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

    private static ClientboundPlayerInfoUpdatePacket profilePacket(List<PigmeeServerPlayer> actors) {
        // ADD_PLAYER supplies tracking identity; UPDATE_LISTED is deliberately absent.
        return new ClientboundPlayerInfoUpdatePacket(EnumSet.of(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), actors);
    }
}
