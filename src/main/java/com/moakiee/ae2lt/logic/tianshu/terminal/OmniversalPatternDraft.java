package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.menu.guisync.PacketWritable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** A complete native pattern keeps recipe identity, molds and input matching together. */
public record OmniversalPatternDraft(ItemStack pattern, @Nullable List<ItemStack> molds) implements PacketWritable {
    private static final OmniversalPatternDraft EMPTY = new OmniversalPatternDraft(ItemStack.EMPTY);

    public OmniversalPatternDraft(ItemStack pattern) {
        this(pattern, null);
    }

    public OmniversalPatternDraft(ItemStack pattern, @Nullable List<ItemStack> molds) {
        this.pattern = pattern == null || pattern.isEmpty() ? ItemStack.EMPTY : pattern.copyWithCount(1);
        this.molds = molds == null ? null : molds.stream().map(stack -> stack.copyWithCount(1)).toList();
    }

    public OmniversalPatternDraft(FriendlyByteBuf data) {
        this(data.readItem(), readMolds(data));
    }

    public static OmniversalPatternDraft empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return pattern.isEmpty();
    }

    public ItemStack pattern() {
        return pattern.copy();
    }

    @Nullable
    public List<ItemStack> molds() {
        return molds == null ? null : molds.stream().map(ItemStack::copy).toList();
    }

    /** Keeps the original native recipe binding even while the editable slots are temporarily invalid. */
    public OmniversalPatternDraft withSlots(List<GenericStack> inputs, List<GenericStack> outputs,
                                          List<ItemStack> molds) {
        var trimmedInputs = trim(inputs);
        var trimmedOutputs = trim(outputs);
        var trimmedMolds = new ArrayList<>(molds);
        while (!trimmedMolds.isEmpty() && trimmedMolds.get(trimmedMolds.size() - 1).isEmpty()) trimmedMolds.remove(trimmedMolds.size() - 1);
        if (isEmpty() && trimmedInputs.isEmpty() && trimmedOutputs.isEmpty() && trimmedMolds.isEmpty()) return this;
        // An unbound draft uses AE2's component container only; it is never placed in the result slot.
        var source = PatternDetailsHelper.encodeProcessingPattern(trimmedInputs.toArray(GenericStack[]::new), trimmedOutputs.toArray(GenericStack[]::new));
        var edited = isEmpty() ? source : pattern.copy();
        var data = edited.getOrCreateTag();
        var sourceData = source.getOrCreateTag();
        data.put("in", sourceData.getList("in", Tag.TAG_COMPOUND).copy());
        data.put("out", sourceData.getList("out", Tag.TAG_COMPOUND).copy());
        return new OmniversalPatternDraft(edited, trimmedMolds);
    }

    private static List<GenericStack> trim(List<GenericStack> stacks) {
        int end = stacks.size();
        while (end > 0 && stacks.get(end - 1) == null) end--;
        return new ArrayList<>(stacks.subList(0, end));
    }

    public CompoundTag write() {
        var result = new CompoundTag();
        if (!pattern.isEmpty()) result.put("Pattern", pattern.save(new CompoundTag()));
        if (molds != null) {
            var list = new ListTag();
            for (var mold : molds) list.add(mold.save(new CompoundTag()));
            result.put("Molds", list);
        }
        return result;
    }

    public static OmniversalPatternDraft read(CompoundTag tag) {
        List<ItemStack> molds = null;
        if (tag.contains("Molds", Tag.TAG_LIST)) {
            molds = new ArrayList<>();
            for (var mold : tag.getList("Molds", Tag.TAG_COMPOUND)) {
                molds.add(ItemStack.of((CompoundTag) mold));
            }
        }
        return new OmniversalPatternDraft(ItemStack.of(tag.getCompound("Pattern")), molds);
    }

    @Override
    public void writeToPacket(FriendlyByteBuf data) {
        data.writeItem(pattern);
        data.writeBoolean(molds != null);
        if (molds != null) {
            data.writeVarInt(molds.size());
            for (var mold : molds) data.writeItem(mold);
        }
    }

    @Nullable
    private static List<ItemStack> readMolds(FriendlyByteBuf data) {
        if (!data.readBoolean()) return null;
        int count = data.readVarInt();
        if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid mold slot count");
        var molds = new ArrayList<ItemStack>(count);
        for (int i = 0; i < count; i++) molds.add(data.readItem());
        return molds;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof OmniversalPatternDraft draft) || !ItemStack.matches(pattern, draft.pattern)) return false;
        if (molds == null || draft.molds == null) return molds == draft.molds;
        if (molds.size() != draft.molds.size()) return false;
        for (int i = 0; i < molds.size(); i++) {
            if (!ItemStack.matches(molds.get(i), draft.molds.get(i))) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = java.util.Objects.hash(pattern.getItem(), pattern.getTag());
        if (molds != null) for (var mold : molds) hash = hash * 31 + java.util.Objects.hash(mold.getItem(), mold.getTag());
        return hash;
    }
}
