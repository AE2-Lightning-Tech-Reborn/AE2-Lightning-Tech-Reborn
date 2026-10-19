package com.moakiee.ae2lt.compat;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class LegacyCreativeTab {
    private LegacyCreativeTab() {}
    public static Supplier<CreativeModeTab> create(String name, Supplier<ItemStack> icon, Consumer<Output> items) {
        var tab = new CreativeModeTab(name) {
            @Override public ItemStack makeIcon() { return icon.get(); }
            @Override public void fillItemList(NonNullList<ItemStack> stacks) { items.accept(new Output(stacks)); }
        };
        return () -> tab;
    }
    public record Output(NonNullList<ItemStack> items) {
        public void accept(ItemLike item) { accept(new ItemStack(item)); }
        public void accept(ItemStack stack) { items.add(stack); }
    }
}
