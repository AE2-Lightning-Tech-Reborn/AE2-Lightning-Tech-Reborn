package com.moakiee.ae2lt.recipe.compat;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** Keeps existing AE2 stack NBT layouts while AE2 switches to ValueInput/Output. */
public final class LegacyAeStackTags {
    private LegacyAeStackTags() {
    }

    public static GenericStack readGeneric(HolderLookup.Provider registries, CompoundTag tag) {
        return GenericStack.readTag(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
    }

    public static CompoundTag writeGeneric(HolderLookup.Provider registries, GenericStack stack) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        GenericStack.writeTag(output, stack);
        return output.buildResult();
    }

    public static AEKey readKey(HolderLookup.Provider registries, CompoundTag tag) {
        return AEKey.fromTagGeneric(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
    }

    public static CompoundTag writeKey(HolderLookup.Provider registries, AEKey key) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        key.toTagGeneric(output);
        return output.buildResult();
    }
}
