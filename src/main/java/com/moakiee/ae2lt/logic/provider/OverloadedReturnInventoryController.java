package com.moakiee.ae2lt.logic.provider;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/** Owns the full return buffer and the nine-slot GUI page projected from it. */
final class OverloadedReturnInventoryController {
    private static final int PATTERNS_PER_PAGE = 36;
    private static final int RETURN_SLOTS_PER_PAGE = 9;

    private final UnlimitedReturnInventory full;
    private final UnlimitedReturnInventory pageView;
    private final Runnable changeListener;
    private final GenericStack[] pageSnapshot =
            new GenericStack[RETURN_SLOTS_PER_PAGE];
    private final int totalPages;
    private boolean syncing;
    private int currentPage;

    OverloadedReturnInventoryController(
            int patternCapacity,
            Runnable changeListener,
            ReturnSlotFilter filter) {
        this.changeListener = changeListener;
        totalPages = Math.max(1,
                (patternCapacity + PATTERNS_PER_PAGE - 1) / PATTERNS_PER_PAGE);
        int fullSlots = totalPages * RETURN_SLOTS_PER_PAGE;
        full = fullSlots > RETURN_SLOTS_PER_PAGE
                ? UnlimitedReturnInventory.create(this::onFullChanged, filter, fullSlots)
                : UnlimitedReturnInventory.create(this::onFullChanged, filter);
        pageView = UnlimitedReturnInventory.create(() -> {
            if (!syncing) {
                copyPageToFull();
            }
        }, filter);
    }

    private void onFullChanged() {
        if (syncing) return;
        // The full buffer can change between menu broadcasts. Keep a following
        // GUI edit from replacing a newer stack or reviving a drained stack.
        if (pageView != null) copyFullToPage();
        changeListener.run();
    }

    UnlimitedReturnInventory full() {
        return full;
    }

    UnlimitedReturnInventory pageView() {
        return pageView;
    }

    void setAdmissionPolicy(Predicate<AEKey> admissionPolicy) {
        full.setAdmissionPolicy(admissionPolicy);
        pageView.setAdmissionPolicy(admissionPolicy);
    }

    void setOnlineSupplier(BooleanSupplier online) {
        full.setOnlineSupplier(online);
        pageView.setOnlineSupplier(online);
    }

    int currentPage() {
        return currentPage;
    }

    int totalPages() {
        return totalPages;
    }

    void setCurrentPage(int page) {
        int bounded = Math.max(0, Math.min(page, totalPages - 1));
        if (bounded == currentPage) {
            return;
        }
        copyPageToFull();
        currentPage = bounded;
        copyFullToPage();
    }

    void copyFullToPage() {
        syncing = true;
        try {
            int offset = currentPage * RETURN_SLOTS_PER_PAGE;
            for (int i = 0; i < RETURN_SLOTS_PER_PAGE; i++) {
                int fullIndex = offset + i;
                var stack = fullIndex < full.size()
                        ? full.getStack(fullIndex)
                        : null;
                pageView.setStack(i, stack);
                pageSnapshot[i] = stack;
            }
        } finally {
            syncing = false;
        }
    }

    /** Copy only player-modified slots, preserving newer external inserts. */
    void copyPageToFull() {
        if (syncing) return;
        boolean changed = false;
        syncing = true;
        try {
            int offset = currentPage * RETURN_SLOTS_PER_PAGE;
            for (int i = 0; i < RETURN_SLOTS_PER_PAGE; i++) {
                int fullIndex = offset + i;
                if (fullIndex >= full.size()) {
                    continue;
                }
                var current = pageView.getStack(i);
                if (Objects.equals(current, pageSnapshot[i])) {
                    continue;
                }
                changed = true;
                full.setStack(fullIndex, current);
                pageSnapshot[i] = current;
            }
        } finally {
            syncing = false;
            try {
                copyFullToPage();
            } finally {
                // Notify once after the complete page is committed: callbacks
                // may insert into full and must not be overwritten by later slots.
                if (changed) changeListener.run();
            }
        }
    }
}
