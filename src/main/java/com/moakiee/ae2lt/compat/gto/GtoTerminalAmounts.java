package com.moakiee.ae2lt.compat.gto;

import java.math.BigInteger;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import com.moakiee.thunderbolt.ae2.crafting.ExactAmountFormatter;

/** Only changes the active network slot; recipe overlays and requestable quantities stay native. */
public final class GtoTerminalAmounts {
    private record Entry(AEKey key, BigInteger amount) { }
    private static final ThreadLocal<Entry> CURRENT = new ThreadLocal<>();
    private GtoTerminalAmounts() { }

    public static void with(AEKey key, BigInteger amount, Runnable render) {
        var previous = CURRENT.get();
        if (key != null && amount != null) CURRENT.set(new Entry(key, amount));
        else CURRENT.remove();
        try { render.run(); }
        finally { if (previous != null) CURRENT.set(previous); else CURRENT.remove(); }
    }

    public static String format(AEKey key, AmountFormat format) {
        var entry = CURRENT.get();
        if (entry == null || entry.key() != key || (format != AmountFormat.SLOT && format != AmountFormat.SLOT_LARGE_FONT)) return null;
        return ExactAmountFormatter.slot(entry.amount(), key.getAmountPerUnit());
    }
}
