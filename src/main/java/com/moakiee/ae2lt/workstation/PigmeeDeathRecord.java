package com.moakiee.ae2lt.workstation;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** One terminal event per actor identity; never replayed as a broadcast on load. */
public record PigmeeDeathRecord(UUID eventId, UUID actorId, UUID ownerId, PigmeeVariant variant,
        ResourceLocation dimension, Vec3 exactPosition, Component name, String ownerName,
        Component reason, long gameTime) {
    public BlockPos position() { return BlockPos.containing(exactPosition); }

    CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putUUID("Event", eventId);
        tag.putUUID("Actor", actorId);
        tag.putUUID("Owner", ownerId);
        tag.putString("Variant", variant.name());
        tag.putString("Dimension", dimension.toString());
        tag.putDouble("X", exactPosition.x); tag.putDouble("Y", exactPosition.y); tag.putDouble("Z", exactPosition.z);
        tag.putString("Name", Component.Serializer.toJson(name, registries));
        tag.putString("OwnerName", ownerName);
        tag.putString("Reason", Component.Serializer.toJson(reason, registries));
        tag.putLong("Time", gameTime);
        return tag;
    }

    static PigmeeDeathRecord load(CompoundTag tag, HolderLookup.Provider registries) {
        var name = Component.Serializer.fromJson(tag.getString("Name"), registries);
        var reason = Component.Serializer.fromJson(tag.getString("Reason"), registries);
        if (name == null || reason == null || !tag.contains("OwnerName", Tag.TAG_STRING)
                || !tag.contains("Time", Tag.TAG_LONG)) throw new IllegalArgumentException("Malformed death record");
        for (String axis : new String[] {"X", "Y", "Z"}) {
            if (!tag.contains(axis, Tag.TAG_DOUBLE) || !Double.isFinite(tag.getDouble(axis))) {
                throw new IllegalArgumentException("Malformed death position");
            }
        }
        return new PigmeeDeathRecord(tag.getUUID("Event"), tag.getUUID("Actor"), tag.getUUID("Owner"),
                PigmeeVariant.valueOf(tag.getString("Variant")), ResourceLocation.parse(tag.getString("Dimension")),
                new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                name, tag.getString("OwnerName"), reason, tag.getLong("Time"));
    }

    public Component message() {
        var pos = position();
        return Component.translatable("message.ae2lt.pigmee_death", ownerName, name, dimension.toString(),
                pos.getX(), pos.getY(), pos.getZ(), reason);
    }
}
