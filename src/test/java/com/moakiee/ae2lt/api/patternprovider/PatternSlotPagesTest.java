package com.moakiee.ae2lt.api.patternprovider;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import appeng.menu.slot.AppEngSlot;
import appeng.util.inv.AppEngInternalInventory;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PatternSlotPagesTest {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void partialPagesClampExtremeRequestsWithoutChangingInventory() {
        var inventory = new AppEngInternalInventory(null, 73);
        inventory.setItemDirect(72, new ItemStack(Items.DIAMOND));
        var slots = slots(inventory);
        var pages = new PatternSlotPages(slots);
        assertEquals(3, pages.totalPages());
        assertEquals(2, pages.showPage(Integer.MAX_VALUE));
        for (int i = 0; i < slots.size(); i++) assertEquals(i == 72, slots.get(i).isActive());
        assertEquals(0, pages.showPage(Integer.MIN_VALUE));
        for (int i = 0; i < slots.size(); i++) assertEquals(i < 36, slots.get(i).isActive());
        assertTrue(inventory.getStackInSlot(72).is(Items.DIAMOND));
        assertEquals(1, new PatternSlotPages(List.of()).totalPages());
        assertEquals(0, new PatternSlotPages(List.of()).showPage(Integer.MAX_VALUE));
    }

    @Test
    void viewersKeepIndependentPagesAndResizeRealignsEveryPage() {
        var inventory = new AppEngInternalInventory(null, 144);
        var first = slots(inventory);
        var second = slots(inventory);
        var firstPages = new PatternSlotPages(first);
        var secondPages = new PatternSlotPages(second);
        firstPages.showPage(3);
        secondPages.showPage(0);
        assertTrue(first.get(143).isActive());
        assertFalse(second.get(143).isActive());
        assertTrue(second.getFirst().isActive());
        for (int offset : new int[]{0, 50}) {
            for (int i = 0; i < 36; i++) {
                first.get(i).x = offset + i % 9 * 18;
                first.get(i).y = offset + i / 9 * 18;
            }
            PatternSlotPages.alignSlots(first);
            for (int i = 36; i < 144; i++) {
                assertEquals(first.get(i % 36).x, first.get(i).x);
                assertEquals(first.get(i % 36).y, first.get(i).y);
                assertEquals(i >= 108, first.get(i).isActive());
            }
        }
    }

    private static List<Slot> slots(AppEngInternalInventory inventory) {
        var slots = new ArrayList<Slot>();
        for (int i = 0; i < inventory.size(); i++) slots.add(new AppEngSlot(inventory, i));
        return slots;
    }
}
