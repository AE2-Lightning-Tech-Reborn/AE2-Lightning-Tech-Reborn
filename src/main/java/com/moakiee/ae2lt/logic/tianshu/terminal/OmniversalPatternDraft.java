package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.menu.guisync.PacketWritable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Native recipe identity and editable sparse inputs survive independently of the result slot. */
public record OmniversalPatternDraft(ItemStack pattern, @Nullable List<ItemStack> molds) implements PacketWritable {
    private static final OmniversalPatternDraft EMPTY = new OmniversalPatternDraft(ItemStack.EMPTY);
    public OmniversalPatternDraft(ItemStack pattern) { this(pattern, null); }
    public OmniversalPatternDraft {
        pattern = pattern == null || pattern.isEmpty() ? ItemStack.EMPTY : pattern.copyWithCount(1);
        if (molds != null) {
            if (molds.size() > 256) throw new IllegalArgumentException("Too many mold slots");
            molds = molds.stream().map(stack -> stack.copyWithCount(1)).toList();
        }
    }
    public OmniversalPatternDraft(FriendlyByteBuf data) { this(data.readItem(), readMolds(data)); }
    public static OmniversalPatternDraft empty() { return EMPTY; }
    public boolean isEmpty() { return pattern.isEmpty(); }
    @Override public ItemStack pattern() { return pattern.copy(); }
    @Override public List<ItemStack> molds() { return molds == null ? null : molds.stream().map(ItemStack::copy).toList(); }
    public List<GenericStack> inputs() { return stacks(pattern, "in", 81); }
    public List<GenericStack> outputs() { return stacks(pattern, "out", 27); }

    public OmniversalPatternDraft withSlots(List<GenericStack> inputs, List<GenericStack> outputs, List<ItemStack> molds) {
        if (inputs.size() > 81 || outputs.size() > 27) throw new IllegalArgumentException("Too many pattern slots");
        var editedMolds = new ArrayList<>(molds);
        while (!editedMolds.isEmpty() && editedMolds.get(editedMolds.size() - 1).isEmpty()) editedMolds.remove(editedMolds.size() - 1);
        var in = stackTags(inputs); var out = stackTags(outputs);
        if (isEmpty() && in.isEmpty() && out.isEmpty() && editedMolds.isEmpty()) return this;
        var edited = isEmpty() ? AEItems.PROCESSING_PATTERN.stack() : pattern.copy();
        edited.getOrCreateTag().put("in", in);
        edited.getOrCreateTag().put("out", out);
        return new OmniversalPatternDraft(edited, editedMolds);
    }

    private static ListTag stackTags(List<GenericStack> stacks) {
        int end = stacks.size();
        while (end > 0 && stacks.get(end - 1) == null) end--;
        var result = new ListTag();
        for (int i = 0; i < end; i++) result.add(GenericStack.writeTag(stacks.get(i)));
        return result;
    }
    private static List<GenericStack> stacks(ItemStack pattern, String key, int limit) {
        var tag = pattern.getTag();
        if (tag == null) return List.of();
        var entries = tag.getList(key, Tag.TAG_COMPOUND);
        if (entries.size() > limit) return List.of();
        var result = new ArrayList<GenericStack>(entries.size());
        for (var entry : entries) result.add(GenericStack.readTag((CompoundTag) entry));
        return java.util.Collections.unmodifiableList(result);
    }
    public CompoundTag write() {
        var tag = new CompoundTag();tag.put("Pattern", pattern.save(new CompoundTag()));
        if (molds != null) { var list = new ListTag();for (var mold : molds) list.add(mold.save(new CompoundTag()));tag.put("Molds", list); }
        return tag;
    }
    public static OmniversalPatternDraft read(CompoundTag tag) {
        List<ItemStack> molds = null;
        if (tag.contains("Molds", Tag.TAG_LIST)) {
            var entries = tag.getList("Molds", Tag.TAG_COMPOUND);
            if (entries.size() > 256) return empty();
            molds = new ArrayList<>();for (var entry : entries) molds.add(ItemStack.of((CompoundTag) entry));
        }
        return new OmniversalPatternDraft(ItemStack.of(tag.getCompound("Pattern")), molds);
    }
    @Override public void writeToPacket(FriendlyByteBuf data) {
        data.writeItem(pattern);data.writeBoolean(molds != null);
        if (molds != null) { data.writeVarInt(molds.size());for (var mold : molds) data.writeItem(mold); }
    }
    private static List<ItemStack> readMolds(FriendlyByteBuf data) {
        if (!data.readBoolean()) return null;
        int count = data.readVarInt();
        if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid mold slot count");
        var molds = new ArrayList<ItemStack>(count);for (int i = 0; i < count; i++) molds.add(data.readItem());return molds;
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof OmniversalPatternDraft draft) || !ItemStack.matches(pattern, draft.pattern)) return false;
        if (molds == null || draft.molds == null) return molds == draft.molds;
        if (molds.size() != draft.molds.size()) return false;
        for (int i = 0; i < molds.size(); i++) if (!ItemStack.matches(molds.get(i), draft.molds.get(i))) return false;
        return true;
    }
    @Override public int hashCode() {
        int hash = Objects.hash(pattern.getItem(), pattern.getTag());
        if (molds != null) for (var mold : molds) hash = hash * 31 + Objects.hash(mold.getItem(), mold.getTag());
        return hash;
    }
}
