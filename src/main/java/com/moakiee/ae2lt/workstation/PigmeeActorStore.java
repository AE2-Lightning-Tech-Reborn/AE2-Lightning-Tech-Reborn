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
        final PigmeeVariant variant;
        CompoundTag origin;
        PigmeeDeathRecord death;
        String ownerName = "";
        State state;
        CompoundTag playerData;
        String diagnostic = "";

        Record(UUID id, UUID owner, PigmeeVariant variant, State state, CompoundTag playerData) {
            this.id = id;
            this.owner = owner;
            this.variant = variant;
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
            if (tag.getInt("Schema") < 1 || tag.getInt("Schema") > 3) throw new IllegalArgumentException("unsupported schema");
            if (!tag.contains("CleanShutdown", Tag.TAG_BYTE)) throw new IllegalArgumentException("missing shutdown state");
            ListTag actorRows = requireList(tag, "Actors", Tag.TAG_COMPOUND);
            if (actorRows.size() > 8) throw new IllegalArgumentException("unsupported actor count");
            for (Tag entry : actorRows) {
                CompoundTag row = (CompoundTag) entry;
                UUID id = row.getUUID("Id");
                UUID owner = row.getUUID("Owner");
                if (id.equals(owner)) throw new IllegalArgumentException("owner identity reused");
                Record record = new Record(id, owner, tag.getInt("Schema") == 1 ? PigmeeVariant.NORMAL : PigmeeVariant.valueOf(row.getString("Variant")),
                        State.valueOf(row.getString("State")), row.getCompound("Player").copy());
                validatePlayerData(record.playerData, registries);
                if (!record.playerData.hasUUID("UUID") || !id.equals(record.playerData.getUUID("UUID"))) {
                    throw new IllegalArgumentException("player identity mismatch");
                }
                record.diagnostic = row.getString("Diagnostic");
                record.ownerName = row.getString("OwnerName");
                if (row.contains("Death")) {
                    if (!row.contains("Death", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Malformed death record");
                    record.death = PigmeeDeathRecord.load(row.getCompound("Death"), registries);
                    if (record.state != State.DEAD || !record.death.actorId().equals(id)
                            || !record.death.ownerId().equals(owner) || record.death.variant() != record.variant) {
                        throw new IllegalArgumentException("Death record identity/state mismatch");
                    }
                }
                if (row.contains("Origin", Tag.TAG_COMPOUND)) record.origin = row.getCompound("Origin").copy();
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

    private static ListTag requireList(CompoundTag tag, String key, int elementType) {
        if (!(tag.get(key) instanceof ListTag list) || !list.isEmpty() && list.getElementType() != elementType) {
            throw new IllegalArgumentException("malformed list " + key);
        }
        return list;
    }

    private static void validatePlayerData(CompoundTag player, HolderLookup.Provider registries) {
        if (!player.contains("Dimension", Tag.TAG_STRING)
                || net.minecraft.resources.ResourceLocation.tryParse(player.getString("Dimension")) == null) {
            throw new IllegalArgumentException("missing or invalid dimension");
        }
        ListTag pos = requireList(player, "Pos", Tag.TAG_DOUBLE);
        ListTag motion = requireList(player, "Motion", Tag.TAG_DOUBLE);
        ListTag rotation = requireList(player, "Rotation", Tag.TAG_FLOAT);
        if (pos.size() != 3 || motion.size() != 3 || rotation.size() != 2) throw new IllegalArgumentException("malformed pose");
        for (int i = 0; i < 3; i++) if (!Double.isFinite(pos.getDouble(i)) || !Double.isFinite(motion.getDouble(i))) throw new IllegalArgumentException("non-finite pose");
        for (int i = 0; i < 2; i++) if (!Float.isFinite(rotation.getFloat(i))) throw new IllegalArgumentException("non-finite rotation");
        for (String inventory : new String[] {"Inventory", "EnderItems"}) {
            java.util.Set<Integer> slots = new java.util.HashSet<>();
            for (Tag item : requireList(player, inventory, Tag.TAG_COMPOUND)) {
                CompoundTag stack = (CompoundTag) item;
                int slot = stack.getByte("Slot") & 255;
                boolean validSlot = inventory.equals("EnderItems") ? slot < 27
                        : slot < 36 || slot >= 100 && slot <= 103 || slot == 150;
                if (!stack.contains("Slot", Tag.TAG_BYTE) || !validSlot || !slots.add(slot)) {
                    throw new IllegalArgumentException("invalid or duplicate inventory slot");
                }
                if (net.minecraft.world.item.ItemStack.parse(registries, item).isEmpty()) {
                    throw new IllegalArgumentException("unreadable stack in " + inventory);
                }
            }
        }
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
        tag.putInt("Schema", 3);
        tag.putBoolean("CleanShutdown", cleanShutdown);
        ListTag actors = new ListTag();
        for (Record record : records.values()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Id", record.id);
            row.putUUID("Owner", record.owner);
            row.putString("OwnerName", record.ownerName);
            if (record.death != null) row.put("Death", record.death.save(registries));
            row.putString("Variant", record.variant.name());
            if (record.origin != null) row.put("Origin", record.origin.copy());
            row.putString("State", record.state.name());
            row.putString("Diagnostic", record.diagnostic);
            row.put("Player", record.playerData.copy());
            actors.add(row);
        }
        tag.put("Actors", actors);
        return tag;
    }
}
