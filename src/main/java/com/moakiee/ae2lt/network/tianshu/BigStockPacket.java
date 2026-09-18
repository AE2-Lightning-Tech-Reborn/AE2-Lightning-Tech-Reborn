package com.moakiee.ae2lt.network.tianshu;

import appeng.api.stacks.AEKey;

import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReport;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.math.BigInteger;
import java.util.*;

/** Bounded deltas tied to the existing terminal container; no independent UI session. */
public record BigStockPacket(int containerId, Map<AEKey, BigInteger> changed)
        implements CustomPacketPayload {
    public static final Type<BigStockPacket> TYPE = new Type<>(NetworkInit.id("big_stock"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BigStockPacket> STREAM_CODEC =
            StreamCodec.ofMember(BigStockPacket::write, BigStockPacket::read);

    private void write(RegistryFriendlyByteBuf b) {
        b.writeVarInt(containerId);
        b.writeVarInt(changed.size());
        changed.forEach(
                (k, n) -> {
                    AEKey.writeKey(b, k);
                    ExactPlanReport.writeAmount(b, n);
                });
    }

    private static BigStockPacket read(RegistryFriendlyByteBuf b) {
        int id = b.readVarInt(), n = b.readVarInt();
        if (n < 0 || n > 64) throw new IllegalArgumentException("Invalid exact stock page");
        var result = new LinkedHashMap<AEKey, BigInteger>();
        for (int i = 0; i < n; i++)
            result.put(Objects.requireNonNull(AEKey.readKey(b)), ExactPlanReport.readAmount(b));
        return new BigStockPacket(id, Map.copyOf(result));
    }

    public Type<BigStockPacket> type() {
        return TYPE;
    }

    public static void handle(BigStockPacket p, IPayloadContext c) {
        c.enqueueWork(
                () -> {
                    if (c.player().containerMenu instanceof TianshuPatternEncodingTermMenu m
                            && m.containerId == p.containerId) m.applyBigStock(p.changed);
                });
    }
}
