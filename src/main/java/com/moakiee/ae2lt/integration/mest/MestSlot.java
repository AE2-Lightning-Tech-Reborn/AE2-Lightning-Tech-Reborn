package com.moakiee.ae2lt.integration.mest;

import appeng.api.inventories.InternalInventory;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Stable outer slot identity; the original slot and its native controller retain their indices. */
public interface MestSlot {
    MEStorageMenu controller();
    Slot original();

    static Slot wrap(MEStorageMenu menu, Slot slot) {
        return slot instanceof FakeSlot fake ? new Ghost(menu, fake) : new Real(menu, slot);
    }

    private static InternalInventory inventory(Slot slot) {
        return new InternalInventory() {
            @Override public int size() { return 1; }
            @Override public ItemStack getStackInSlot(int index) { return slot.getItem(); }
            @Override public void setItemDirect(int index, ItemStack stack) { slot.set(stack); }
            @Override public int getSlotLimit(int index) { return slot.getMaxStackSize(); }
            @Override public boolean isItemValid(int index, ItemStack stack) { return slot.mayPlace(stack); }
        };
    }

    final class Real extends AppEngSlot implements MestSlot {
        private final MEStorageMenu controller;
        private final Slot original;
        Real(MEStorageMenu controller, Slot original) {
            super(inventory(original), 0);
            this.controller = controller;
            this.original = original;
            x = y = -9999;
        }
        @Override public MEStorageMenu controller() { return controller; }
        @Override public Slot original() { return original; }
        @Override public boolean isActive() { return original.isActive(); }
        @Override public boolean isSlotEnabled() {
            return original instanceof AppEngSlot slot ? slot.isSlotEnabled() : original.isActive();
        }
        @Override public boolean mayPlace(ItemStack stack) { return isSlotEnabled() && original.mayPlace(stack); }
        @Override public boolean mayPickup(Player player) { return isSlotEnabled() && original.mayPickup(player); }
        @Override public ItemStack remove(int count) { return original.remove(count); }
        @Override public void onTake(Player player, ItemStack stack) { original.onTake(player, stack); }
        @Override public void set(ItemStack stack) { original.set(stack); }
        @Override public void initialize(ItemStack stack) {
            if (original instanceof AppEngSlot slot) slot.initialize(stack); else original.set(stack);
        }
    }

    final class Ghost extends FakeSlot implements MestSlot {
        private final MEStorageMenu controller;
        private final FakeSlot original;
        Ghost(MEStorageMenu controller, FakeSlot original) {
            super(inventory(original), 0);
            this.controller = controller;
            this.original = original;
            x = y = -9999;
        }
        @Override public MEStorageMenu controller() { return controller; }
        @Override public Slot original() { return original; }
        @Override public boolean isActive() { return original.isActive(); }
        @Override public boolean isSlotEnabled() { return original.isSlotEnabled(); }
        @Override public boolean canSetFilterTo(ItemStack stack) { return original.canSetFilterTo(stack); }
        @Override public void set(ItemStack stack) { original.set(stack); }
        @Override public void initialize(ItemStack stack) { original.initialize(stack); }
    }
}
