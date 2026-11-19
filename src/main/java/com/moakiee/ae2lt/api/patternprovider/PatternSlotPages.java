package com.moakiee.ae2lt.api.patternprovider;

import java.util.List;

import net.minecraft.world.inventory.Slot;
import appeng.menu.slot.AppEngSlot;

/**
 * Shared slot paging for provider menus. Each menu owns its own instance;
 * this class never changes the provider inventory or its advertised patterns.
 * Safe to use on both logical sides.
 */
public final class PatternSlotPages {
    public static final int SLOTS_PER_PAGE = 36;

    private final List<Slot> slots;
    private int shownPage = -1;

    public PatternSlotPages(List<Slot> slots) {
        this.slots = List.copyOf(slots);
    }

    public int totalPages() {
        return Math.max(1, Math.ceilDiv(slots.size(), SLOTS_PER_PAGE));
    }

    /** Applies the visible page and returns its clamped index. */
    public int showPage(int page) {
        int clamped = Math.clamp(page, 0, totalPages() - 1);
        if (shownPage != clamped) {
            int start = clamped * SLOTS_PER_PAGE;
            for (int i = 0; i < slots.size(); i++) {
                if (slots.get(i) instanceof AppEngSlot slot) {
                    slot.setActive(i >= start && i < start + SLOTS_PER_PAGE);
                }
            }
            shownPage = clamped;
        }
        return clamped;
    }

    /** Call after the screen style has positioned the first page, including on resize. */
    public static void alignSlots(List<Slot> slots) {
        for (int i = SLOTS_PER_PAGE; i < slots.size(); i++) {
            var firstPageSlot = slots.get(i % SLOTS_PER_PAGE);
            slots.get(i).x = firstPageSlot.x;
            slots.get(i).y = firstPageSlot.y;
        }
    }
}
