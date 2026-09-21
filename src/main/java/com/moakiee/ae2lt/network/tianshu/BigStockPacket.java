package com.moakiee.ae2lt.network.tianshu;

import appeng.api.stacks.AEKey;

import com.moakiee.ae2lt.client.ClientNetworkPacketHandlers;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReport;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Supplier;

/** Bounded deltas tied to the existing terminal container; no independent UI session. */
public record BigStockPacket(int containerId, Map<AEKey, BigInteger> changed)
         {
    public void write(FriendlyByteBuf b) {
        b.writeVarInt(containerId);
        b.writeVarInt(changed.size());
        changed.forEach(
                (k, n) -> {
                    AEKey.writeKey(b, k);
                    ExactPlanReport.writeAmount(b, n);
                });
    }

    public static BigStockPacket read(FriendlyByteBuf b) {
        int id = b.readVarInt(), n = b.readVarInt();
        if (n < 0 || n > 64) throw new IllegalArgumentException("Invalid exact stock page");
        var result = new LinkedHashMap<AEKey, BigInteger>();
        for (int i = 0; i < n; i++)
            result.put(Objects.requireNonNull(AEKey.readKey(b)), ExactPlanReport.readAmount(b));
        return new BigStockPacket(id, Map.copyOf(result));
    }


    public static void handle(BigStockPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientNetworkPacketHandlers.handleBigStock(packet)));
        ctx.setPacketHandled(true);
    }
}
