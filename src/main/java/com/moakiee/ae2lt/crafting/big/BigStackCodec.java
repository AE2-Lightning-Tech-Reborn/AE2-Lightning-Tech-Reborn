package com.moakiee.ae2lt.crafting.big;

import appeng.api.stacks.AEKey;

import com.moakiee.thunderbolt.core.storage.big.BigAmounts;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;

import java.math.BigInteger;
import java.util.*;

/**
 * Exact persistent quantities; malformed entries fail the load instead of silently losing stock.
 */
public final class BigStackCodec {
    private BigStackCodec() {}

    public static ListTag write(Map<AEKey, BigInteger> values, HolderLookup.Provider registries) {
        var list = new ListTag();
        values.forEach(
                (key, n) -> {
                    BigAmounts.nonNegative(n);
                    if (n.signum() == 0) return;
                    var tag = new CompoundTag();
                    tag.put("key", key.toTagGeneric());
                    tag.putByteArray("amount", n.toByteArray());
                    list.add(tag);
                });
        return list;
    }

    public static Map<AEKey, BigInteger> read(ListTag list, HolderLookup.Provider registries) {
        var values = new LinkedHashMap<AEKey, BigInteger>();
        for (var raw : list) {
            var tag = (CompoundTag) raw;
            var key = AEKey.fromTagGeneric(tag.getCompound("key"));
            if (key == null) throw new IllegalArgumentException("Unknown item in exact inventory");
            var n = BigAmounts.nonNegative(new BigInteger(tag.getByteArray("amount")));
            if (n.signum() > 0) values.merge(key, n, BigInteger::add);
        }
        return values;
    }
}
