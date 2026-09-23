package com.moakiee.ae2lt.recipe.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Keeps the old typed presence check while Minecraft 26 exposes only contains(key). */
public final class LegacyNbtTypes {
    private LegacyNbtTypes() {
    }

    public static boolean contains(CompoundTag compound, String key, int type) {
        Tag value = compound.get(key);
        if (value == null) {
            return false;
        }
        if (type == 99) { // Pre-26 TAG_ANY_NUMERIC.
            int id = value.getId();
            return id >= Tag.TAG_BYTE && id <= Tag.TAG_DOUBLE;
        }
        return value.getId() == type;
    }
}
