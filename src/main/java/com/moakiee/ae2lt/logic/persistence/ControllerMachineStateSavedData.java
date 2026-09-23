package com.moakiee.ae2lt.logic.persistence;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.resources.Identifier;
import com.moakiee.thunderbolt.core.LegacySavedDataReader;

/**
 * World-global state for controller-owned machines. The UUID survives controller removal, while
 * runtime ownership claims are deliberately transient and prevent two loaded copies of one
 * controller from running the same state concurrently.
 */
public final class ControllerMachineStateSavedData extends SavedData {
    private static final String DATA_NAME = "ae2lt_controller_machine_states";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_TYPE = "Type";
    private static final String TAG_ID = "Id";
    private static final String TAG_STATE = "State";

    private static SavedDataType<ControllerMachineStateSavedData> type(HolderLookup.Provider registries) {
        return new SavedDataType<>(
                Identifier.fromNamespaceAndPath("ae2lt", "controller_machine_states"),
                ControllerMachineStateSavedData::new,
                CompoundTag.CODEC.xmap(
                        tag -> load(tag, registries),
                        data -> data.save(new CompoundTag(), registries)));
    }

    private final Map<MachineKey, CompoundTag> states = new HashMap<>();
    private final Map<MachineKey, Supplier<CompoundTag>> deferredStateSnapshots = new HashMap<>();
    private final Map<MachineKey, Owner> owners = new HashMap<>();

    public enum MachineType {
        TIANSHU,
        MATRIX
    }

    private record MachineKey(MachineType type, UUID id) {
        private MachineKey {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(id, "id");
        }
    }

    private record Owner(String dimension, long position) {
    }

    public static ControllerMachineStateSavedData get(ServerLevel level) {
        var server = level.getServer();
        var storage = server.overworld().getDataStorage();
        var type = type(server.registryAccess());
        var data = storage.get(type);
        if (data == null) {
            var old = LegacySavedDataReader.read(server, DATA_NAME);
            data = old == null ? new ControllerMachineStateSavedData() : load(old, server.registryAccess());
            storage.set(type, data);
        }
        return data;
    }

    public boolean hasState(MachineType type, UUID id) {
        return type != null && id != null && states.containsKey(new MachineKey(type, id));
    }

    /** Returns a defensive copy; callers must publish changes with {@link #setState}. */
    public CompoundTag getState(MachineType type, UUID id) {
        if (type == null || id == null) return new CompoundTag();
        var state = states.get(new MachineKey(type, id));
        return state == null ? new CompoundTag() : state.copy();
    }

    public void setState(MachineType type, UUID id, CompoundTag state) {
        if (type == null || id == null || state == null) return;
        setOwnedState(type, id, state.copy());
    }

    /**
     * Publishes a freshly-created state without a defensive deep copy. The caller must not mutate
     * {@code state} after this call; ordinary callers should use {@link #setState} instead.
     */
    public void setOwnedState(MachineType type, UUID id, CompoundTag state) {
        if (type == null || id == null || state == null) return;
        var key = new MachineKey(type, id);
        deferredStateSnapshots.remove(key);
        if (!state.equals(states.get(key))) {
            states.put(key, state);
            setDirty();
        }
    }

    /**
     * Marks this data dirty now, but generates the expensive state snapshot only immediately before
     * this SavedData is written. The supplier must return a fresh tag whose ownership is transferred.
     */
    public void deferStateSnapshot(
            MachineType type, UUID id, Supplier<CompoundTag> snapshotSupplier) {
        if (type == null || id == null || snapshotSupplier == null) return;
        deferredStateSnapshots.put(new MachineKey(type, id), snapshotSupplier);
        setDirty();
    }

    public void cancelDeferredStateSnapshot(MachineType type, UUID id) {
        if (type == null || id == null) return;
        deferredStateSnapshots.remove(new MachineKey(type, id));
    }

    private void materializeDeferredStateSnapshots() {
        var pending = new HashMap<>(deferredStateSnapshots);
        for (var entry : pending.entrySet()) {
            var state = entry.getValue().get();
            deferredStateSnapshots.remove(entry.getKey(), entry.getValue());
            if (state != null && !state.equals(states.get(entry.getKey()))) {
                states.put(entry.getKey(), state);
            }
        }
    }

    public boolean claim(MachineType type, UUID id, ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        return claim(type, id, level.dimension().identifier().toString(), pos.asLong());
    }

    public boolean claim(MachineType type, UUID id, String dimension, long position) {
        if (type == null || id == null || dimension == null) return false;
        var key = new MachineKey(type, id);
        var owner = new Owner(dimension, position);
        var current = owners.get(key);
        if (current != null && !current.equals(owner)) return false;
        owners.put(key, owner);
        return true;
    }

    public void release(MachineType type, UUID id, ServerLevel level, BlockPos pos) {
        if (level != null && pos != null) {
            release(type, id, level.dimension().identifier().toString(), pos.asLong());
        }
    }

    public void release(MachineType type, UUID id, String dimension, long position) {
        if (type == null || id == null || dimension == null) return;
        owners.remove(new MachineKey(type, id), new Owner(dimension, position));
    }

    public boolean isOwner(MachineType type, UUID id, ServerLevel level, BlockPos pos) {
        if (type == null || id == null || level == null || pos == null) return false;
        return new Owner(level.dimension().identifier().toString(), pos.asLong())
                .equals(owners.get(new MachineKey(type, id)));
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        materializeDeferredStateSnapshots();
        var entries = new ListTag();
        for (var entry : states.entrySet()) {
            var entryTag = new CompoundTag();
            entryTag.putString(TAG_TYPE, entry.getKey().type().name());
            entryTag.store(TAG_ID, net.minecraft.core.UUIDUtil.CODEC, entry.getKey().id());
            entryTag.put(TAG_STATE, entry.getValue().copy());
            entries.add(entryTag);
        }
        tag.put(TAG_ENTRIES, entries);
        return tag;
    }

    public static ControllerMachineStateSavedData load(
            CompoundTag tag, HolderLookup.Provider registries) {
        var data = new ControllerMachineStateSavedData();
        var entries = tag.getListOrEmpty(TAG_ENTRIES);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompoundOrEmpty(i);
            if (entry.read(TAG_ID, net.minecraft.core.UUIDUtil.CODEC).isEmpty() || !com.moakiee.ae2lt.recipe.compat.LegacyNbtTypes.contains(entry, TAG_STATE, Tag.TAG_COMPOUND)) continue;
            try {
                var type = MachineType.valueOf(entry.getStringOr(TAG_TYPE, ""));
                data.states.put(new MachineKey(type, entry.read(TAG_ID, net.minecraft.core.UUIDUtil.CODEC).orElseThrow()),
                        entry.getCompoundOrEmpty(TAG_STATE).copy());
            } catch (IllegalArgumentException ignored) {
                // A removed machine type must not make the remaining world data unreadable.
            }
        }
        return data;
    }
}
