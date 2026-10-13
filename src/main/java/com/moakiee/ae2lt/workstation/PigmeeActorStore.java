package com.moakiee.ae2lt.workstation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/** One authoritative save file; bodies are never restored from vanilla playerdata. */
final class PigmeeActorStore extends SavedData {
    static final String KEY = "ae2lt_pigmee_p0";
    static final Factory<PigmeeActorStore> FACTORY = new Factory<>(PigmeeActorStore::new, PigmeeActorStore::load);
    enum State { ACTIVE, DORMANT, DEAD, QUARANTINED }

    static final class Record {
        final UUID id;
        final UUID owner;
        State state;
        CompoundTag playerData;
        String diagnostic = "";

        Record(UUID id, UUID owner, State state, CompoundTag playerData) {
            this.id = id;
            this.owner = owner;
            this.state = state;
            this.playerData = playerData;
        }
    }

    final Map<UUID, Record> records = new LinkedHashMap<>();
    final Map<UUID, PigmeeServerPlayer> live = new LinkedHashMap<>();
    private CompoundTag unreadable;
    boolean cleanShutdown;

    boolean writable() { return unreadable == null; }

    private static PigmeeActorStore load(CompoundTag tag, HolderLookup.Provider registries) {
        PigmeeActorStore store = new PigmeeActorStore();
        try {
            if (tag.getInt("Schema") != 1) throw new IllegalArgumentException("unsupported schema");
            for (Tag entry : tag.getList("Actors", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag) entry;
                UUID id = row.getUUID("Id");
                UUID owner = row.getUUID("Owner");
                if (id.equals(owner)) throw new IllegalArgumentException("owner identity reused");
                Record record = new Record(id, owner, State.valueOf(row.getString("State")), row.getCompound("Player").copy());
                if (!record.playerData.hasUUID("UUID") || !id.equals(record.playerData.getUUID("UUID"))) {
                    throw new IllegalArgumentException("player identity mismatch");
                }
                record.diagnostic = row.getString("Diagnostic");
                if (record.state == State.ACTIVE && !tag.getBoolean("CleanShutdown")) {
                    record.state = State.QUARANTINED;
                    record.diagnostic = "Unclean shutdown: inspect world and inventory before administrator recovery";
                }
                if (store.records.putIfAbsent(id, record) != null) throw new IllegalArgumentException("duplicate identity");
            }
        } catch (RuntimeException malformed) {
            // Preserve the entire original payload; never silently replace a corrupt store.
            store.records.clear();
            store.unreadable = tag.copy();
        }
        return store;
    }

    void snapshot(Record record, PigmeeServerPlayer player) {
        record.playerData = player.saveWithoutId(new CompoundTag());
        setDirty();
    }

    @Override
    public boolean isDirty() {
        return super.isDirty() || !live.isEmpty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (unreadable != null) return unreadable.copy();
        // Serialize on world save, not every tick. Snapshots cannot be spent as inventories.
        live.forEach((id, player) -> snapshot(records.get(id), player));
        tag.putInt("Schema", 1);
        tag.putBoolean("CleanShutdown", cleanShutdown);
        ListTag actors = new ListTag();
        for (Record record : records.values()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Id", record.id);
            row.putUUID("Owner", record.owner);
            row.putString("State", record.state.name());
            row.putString("Diagnostic", record.diagnostic);
            row.put("Player", record.playerData.copy());
            actors.add(row);
        }
        tag.put("Actors", actors);
        return tag;
    }
}
