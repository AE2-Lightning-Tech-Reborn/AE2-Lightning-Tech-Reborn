package com.moakiee.ae2lt.recipe.compat;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

/** Reads and writes the four-int UUID layout used by the pre-26 NBT API. */
public final class LegacyNbtUuid {
    private LegacyNbtUuid() {
    }

    public static void put(CompoundTag tag, String key, UUID uuid) {
        tag.putIntArray(key, UUIDUtil.uuidToIntArray(uuid));
    }

    public static boolean has(CompoundTag tag, String key) {
        return tag.getIntArray(key).filter(array -> array.length == 4).isPresent();
    }

    public static UUID get(CompoundTag tag, String key) {
        int[] array = tag.getIntArray(key)
                .filter(value -> value.length == 4)
                .orElseThrow(() -> new IllegalArgumentException("Invalid UUID at " + key));
        return UUIDUtil.uuidFromIntArray(array);
    }
}
