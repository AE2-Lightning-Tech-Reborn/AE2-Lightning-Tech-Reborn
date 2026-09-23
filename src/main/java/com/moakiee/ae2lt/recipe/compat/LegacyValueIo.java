package com.moakiee.ae2lt.recipe.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Lets existing block entities retain their flat NBT layout with 26.1 ValueIO. */
public final class LegacyValueIo {
    private LegacyValueIo() {
    }

    public static CompoundTag readableTag(ValueInput input) {
        if (input instanceof TagValueInput tagInput) {
            return tagInput.input.copy();
        }
        throw new IllegalArgumentException("Unsupported ValueInput: " + input.getClass().getName());
    }

    public static CompoundTag writableTag(ValueOutput output) {
        if (output instanceof TagValueOutput tagOutput) {
            return tagOutput.buildResult();
        }
        throw new IllegalArgumentException("Unsupported ValueOutput: " + output.getClass().getName());
    }

    public static ValueInput input(CompoundTag tag, HolderLookup.Provider registries) {
        return TagValueInput.create(ProblemReporter.DISCARDING, registries, tag);
    }

    public static ValueOutput output(CompoundTag tag, HolderLookup.Provider registries) {
        return new TagValueOutput(ProblemReporter.DISCARDING,
                registries.createSerializationContext(NbtOps.INSTANCE), tag);
    }

    public static ValueInput craftingLinkInput(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag normalized = tag.copy();
        if (!normalized.contains("craftId")) {
            var legacyId = normalized.get("CraftID");
            if (legacyId != null) normalized.put("craftId", legacyId.copy());
        }
        return input(normalized, registries);
    }
}
