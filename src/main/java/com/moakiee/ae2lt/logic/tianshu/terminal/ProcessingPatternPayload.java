package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** AE2 15's native sparse processing NBT, including temporarily empty editor drafts. */
public record ProcessingPatternPayload(List<GenericStack> sparseInputs, List<GenericStack> sparseOutputs) {
    public ProcessingPatternPayload {
        sparseInputs = Collections.unmodifiableList(new ArrayList<>(sparseInputs));
        sparseOutputs = Collections.unmodifiableList(new ArrayList<>(sparseOutputs));
    }

    @Nullable
    public static ProcessingPatternPayload read(ItemStack pattern) {
        var tag = pattern.getTag();
        if (tag == null || !tag.contains("in", Tag.TAG_LIST) || !tag.contains("out", Tag.TAG_LIST)) return null;
        var inputs = tag.getList("in", Tag.TAG_COMPOUND);
        var outputs = tag.getList("out", Tag.TAG_COMPOUND);
        if (inputs.size() > AEProcessingPattern.MAX_INPUT_SLOTS || outputs.size() > AEProcessingPattern.MAX_OUTPUT_SLOTS)
            return null;
        return new ProcessingPatternPayload(decode(inputs), decode(outputs));
    }

    public static void write(ItemStack pattern, List<GenericStack> inputs, List<GenericStack> outputs) {
        var encodedInputs = encode(inputs, AEProcessingPattern.MAX_INPUT_SLOTS);
        var encodedOutputs = encode(outputs, AEProcessingPattern.MAX_OUTPUT_SLOTS);
        var tag = pattern.getOrCreateTag();
        tag.put("in", encodedInputs);
        tag.put("out", encodedOutputs);
    }

    private static List<GenericStack> decode(ListTag list) {
        var result = new ArrayList<GenericStack>(list.size());
        for (int i = 0; i < list.size(); i++) result.add(GenericStack.readTag(list.getCompound(i)));
        return result;
    }

    private static ListTag encode(List<GenericStack> stacks, int limit) {
        if (stacks.size() > limit) throw new IllegalArgumentException("Too many native processing slots");
        var result = new ListTag();
        for (var stack : stacks) result.add(stack == null ? new CompoundTag() : GenericStack.writeTag(stack));
        return result;
    }
}
