package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.util.inv.AppEngInternalInventory;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Terminal-owned inputs and native cell configuration; computed result slots are never stored. */
public final class TianshuWorkstationStorage {
    private CompoundTag data = new CompoundTag();
    private final Consumer<CompoundTag> save;
    private final Map<Object, Runnable> viewers = new IdentityHashMap<>();

    public TianshuWorkstationStorage(Consumer<CompoundTag> save) {
        this.save = save;
    }

    public CompoundTag read() { return data.copy(); }

    public void load(CompoundTag data) {
        this.data = data.copy();
        for (var viewer : List.copyOf(viewers.values())) viewer.run();
    }

    public void subscribe(Object owner, Runnable refresh) { viewers.put(owner, refresh); }
    public void unsubscribe(Object owner) { viewers.remove(owner); }

    public void update(CompoundTag next, Object source) {
        if (data.equals(next)) return;
        data = next.copy();
        save.accept(data.copy());
        // All viewers of a wired terminal must observe consumption before the next player action.
        // Leaving stale native result previews in another menu would permit duplicate extraction.
        for (var entry : List.copyOf(viewers.entrySet()))
            if (entry.getKey() != source) entry.getValue().run();
    }

    public void addDrops(List<ItemStack> drops) {
        for (var entry : data.getList("inputs", Tag.TAG_COMPOUND)) {
            var stack = ItemStack.of((CompoundTag) entry);
            if (!stack.isEmpty()) drops.add(stack);
        }
        var cell = new AppEngInternalInventory(1);
        cell.readFromNBT(data.getCompound("cellWorkbench"), "cell");
        if (!cell.getStackInSlot(0).isEmpty()) drops.add(cell.getStackInSlot(0).copy());
    }

    public static boolean containsItems(CompoundTag data) {
        for (var entry : data.getList("inputs", Tag.TAG_COMPOUND))
            if (((CompoundTag) entry).contains("id", Tag.TAG_STRING)) return true;
        return !data.getCompound("cellWorkbench").getList("cell", Tag.TAG_COMPOUND).isEmpty();
    }

    public void clear() { update(new CompoundTag(), null); }
}
