package com.moakiee.ae2lt.mixin.client;

import java.util.Set;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Native drag-preview state used when LDLib draws an existing menu slot. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("clickedSlot")
    @Nullable Slot ae2lt$getClickedSlot();

    @Accessor("draggingItem")
    ItemStack ae2lt$getDraggingItem();

    @Accessor("isSplittingStack")
    boolean ae2lt$isSplittingStack();

    @Accessor("isQuickCrafting")
    boolean ae2lt$isQuickCrafting();

    @Accessor("quickCraftSlots")
    Set<Slot> ae2lt$getQuickCraftSlots();

    @Accessor("quickCraftingType")
    int ae2lt$getQuickCraftingType();

    @Invoker("recalculateQuickCraftRemaining")
    void ae2lt$recalculateQuickCraftRemaining();
}
