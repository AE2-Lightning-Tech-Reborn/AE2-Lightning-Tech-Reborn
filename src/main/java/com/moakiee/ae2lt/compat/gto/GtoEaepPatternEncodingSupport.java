package com.moakiee.ae2lt.compat.gto;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.items.PatternEncodingTermMenu;

/** GTO consumes blanks directly from ME storage; never fill its now-invisible legacy inventory. */
public final class GtoEaepPatternEncodingSupport {
    private GtoEaepPatternEncodingSupport() { }

    public static void releaseLegacyBlanks(PatternEncodingTermMenu menu) {
        if (menu.isClientSide()) return;
        var host = (IPatternTerminalMenuHost) menu.getTarget();
        var legacy = host.getLogic().getBlankPatternInv();
        var stack = legacy.getStackInSlot(0);
        if (stack.isEmpty() || !AEItems.BLANK_PATTERN.isSameAs(stack)) return;
        var storage = host.getInventory();
        if (storage == null) return;
        long accepted = storage.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, menu.getActionSource());
        if (accepted < 0 || accepted > stack.getCount()) throw new IllegalStateException("Invalid blank-pattern insertion");
        if (accepted > 0) legacy.extractItem(0, (int) accepted, false);
    }

    public static void refund(PatternEncodingTermMenu menu, int count) {
        if (menu.isClientSide() || count <= 0) return;
        var host = (IPatternTerminalMenuHost) menu.getTarget();
        var storage = host.getInventory();
        long accepted = storage == null ? 0 : storage.insert(AEItemKey.of(AEItems.BLANK_PATTERN), count,
                Actionable.MODULATE, menu.getActionSource());
        if (accepted < 0 || accepted > count) throw new IllegalStateException("Invalid blank-pattern refund");
        if (accepted < count) menu.getPlayerInventory().placeItemBackInInventory(AEItems.BLANK_PATTERN.stack((int) (count - accepted)));
    }
}
