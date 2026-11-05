package com.moakiee.ae2lt.dimension;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.grid.FrequencyAccessLevel;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequency;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/** Separate dimension quota and world files; the existing frequency ACL defines the roles. */
public final class PigmeeDimensionData extends SavedData {
    private static final String NAME = "ae2lt_pigmee_dimensions";
    private final Map<Integer, Entry> entries = new LinkedHashMap<>();
    private int nextId = 1;

    public static final class Entry {
        private final WirelessFrequency access;
        private final PigmeeDimensionKind kind;
        private PigmeeChunkGenerator.Palette palette;
        private boolean generated;

        private Entry(WirelessFrequency access, PigmeeDimensionKind kind, PigmeeChunkGenerator.Palette palette) {
            this.access = access;
            this.kind = kind;
            this.palette = palette;
        }

        public int id() { return access.getId(); }
        public WirelessFrequency access() { return access; }
        public PigmeeDimensionKind kind() { return kind; }
        public boolean even() { return kind == PigmeeDimensionKind.EVEN; }
        public PigmeeChunkGenerator.Palette palette() { return palette; }
        public boolean generated() { return generated; }

        public CompoundTag save() { return save(false); }
        public CompoundTag saveForMenu() { return save(true); }

        private CompoundTag save(boolean forMenu) {
            var tag = new CompoundTag();
            access.writeToTag(tag, WirelessFrequency.NBT_SAVE_ALL);
            tag.putString("Kind", kind.name());
            tag.putBoolean("Even", even());
            tag.putString("Frame", PigmeeDimensionMaterials.id(palette.frame()).toString());
            tag.putString("Center", PigmeeDimensionMaterials.id(palette.center()).toString());
            if (palette.custom()) {
                if (forMenu) tag.putByteArray("CellIndices", PigmeeDimensionPattern.encode(palette.cells()));
                else {
                    // Persist resource IDs, but send only 256 bytes when listing many public worlds.
                    var cells = new ListTag();
                    palette.cells().forEach(state -> cells.add(StringTag.valueOf(PigmeeDimensionMaterials.id(state).toString())));
                    tag.put("Cells", cells);
                }
            }
            tag.putBoolean("Generated", generated);
            return tag;
        }

        public static Entry load(CompoundTag tag) {
            var access = new WirelessFrequency();
            access.readFromTag(tag, WirelessFrequency.NBT_SAVE_ALL);
            var kind = PigmeeDimensionKind.load(tag.getString("Kind"), tag.getBoolean("Even"));
            var frame = PigmeeDimensionMaterials.resolve(tag.getString("Frame"), PigmeeDimensionMaterials.defaultFrame());
            var center = PigmeeDimensionMaterials.resolve(tag.getString("Center"), PigmeeDimensionMaterials.defaultCenter());
            java.util.List<net.minecraft.world.level.block.state.BlockState> cells = java.util.List.of();
            if (kind == PigmeeDimensionKind.CUSTOM) {
                if (tag.contains("CellIndices", Tag.TAG_BYTE_ARRAY)) cells = PigmeeDimensionPattern.decode(tag.getByteArray("CellIndices"));
                else {
                    var saved = tag.getList("Cells", Tag.TAG_STRING);
                    cells = saved.size() == PigmeeDimensionPattern.CELL_COUNT
                            ? saved.stream().map(value -> PigmeeDimensionMaterials.resolve(value.getAsString(), PigmeeDimensionMaterials.floor())).toList()
                            : PigmeeDimensionPattern.template(false, frame, center);
                }
            }
            var entry = new Entry(access, kind, new PigmeeChunkGenerator.Palette(frame, center, cells));
            entry.generated = tag.getBoolean("Generated");
            return entry;
        }
    }

    public static PigmeeDimensionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(PigmeeDimensionData::new, PigmeeDimensionData::load), NAME);
    }

    public static PigmeeDimensionData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new PigmeeDimensionData();
        data.nextId = Math.max(1, tag.getInt("NextId"));
        var list = tag.getList("Dimensions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var entry = Entry.load(list.getCompound(i));
            if (entry.id() > 0) {
                data.entries.put(entry.id(), entry);
                data.nextId = Math.max(data.nextId, Math.addExact(entry.id(), 1));
            }
        }
        return data;
    }

    public Entry find(int id) { return entries.get(id); }
    public Collection<Entry> entries() { return java.util.List.copyOf(entries.values()); }

    public long countOwned(UUID player) {
        return entries.values().stream()
                .filter(entry -> entry.access.getPlayerAccess(player) == FrequencyAccessLevel.OWNER).count();
    }

    public Entry create(ServerPlayer player, String name, boolean even, PigmeeChunkGenerator.Palette palette,
            boolean shared) {
        return create(player, name, even ? PigmeeDimensionKind.EVEN : PigmeeDimensionKind.ODD, palette, shared);
    }

    public Entry create(ServerPlayer player, String name, PigmeeDimensionKind kind, PigmeeChunkGenerator.Palette palette,
            boolean shared) {
        if (name.isBlank() || name.length() > WirelessFrequency.MAX_NAME_LENGTH
                || (kind == PigmeeDimensionKind.CUSTOM) != palette.custom()
                || countOwned(player.getUUID()) >= AE2LTCommonConfig.maxOwnedPigmeeDimensionsPerPlayer()
                || nextId == Integer.MAX_VALUE) return null;
        var access = new WirelessFrequency(nextId++, name.strip(), 0xDBA9B4, player,
                shared ? FrequencySecurityLevel.PUBLIC : FrequencySecurityLevel.PRIVATE, "");
        var entry = new Entry(access, kind, palette);
        entries.put(entry.id(), entry);
        setDirty();
        return entry;
    }

    public boolean configure(ServerPlayer actor, int id, String name,
            PigmeeChunkGenerator.Palette palette, boolean shared) {
        var entry = find(id);
        if (entry == null || !entry.access.getPlayerAccess(actor).isOwner()
                || (entry.kind() == PigmeeDimensionKind.CUSTOM) != palette.custom()
                || name.isBlank() || name.length() > WirelessFrequency.MAX_NAME_LENGTH) return false;
        entry.access.setName(name.strip());
        entry.access.setSecurity(shared ? FrequencySecurityLevel.PUBLIC : FrequencySecurityLevel.PRIVATE);
        entry.palette = palette;
        var level = actor.server.getLevel(PigmeeDimensions.key(id));
        if (level != null && level.getChunkSource().getGenerator() instanceof PigmeeChunkGenerator generator) {
            generator.setPalette(palette);
        }
        setDirty();
        return true;
    }

    public boolean changeMember(ServerPlayer actor, int id, UUID target, byte operation) {
        var entry = find(id);
        if (entry == null) return false;
        if (operation == WirelessFrequency.MEMBERSHIP_TRANSFER_OWNERSHIP
                && entry.access.getPlayerAccess(target) != FrequencyAccessLevel.OWNER
                && countOwned(target) >= AE2LTCommonConfig.maxOwnedPigmeeDimensionsPerPlayer()) return false;
        if (entry.access.changeMembership(actor, target, operation) != WirelessFrequency.RESPONSE_SUCCESS) return false;
        setDirty();
        return true;
    }

    public void markGenerated(Entry entry) {
        entry.generated = true;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("NextId", nextId);
        var list = new ListTag();
        entries.values().forEach(entry -> list.add(entry.save()));
        tag.put("Dimensions", list);
        return tag;
    }
}
