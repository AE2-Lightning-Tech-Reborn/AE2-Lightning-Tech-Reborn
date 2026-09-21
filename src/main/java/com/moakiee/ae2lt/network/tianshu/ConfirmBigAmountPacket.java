package com.moakiee.ae2lt.network.tianshu;

import com.moakiee.ae2lt.crafting.big.BigAmountMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import com.moakiee.thunderbolt.core.storage.big.BigAmounts;

import net.minecraft.network.FriendlyByteBuf;

import java.math.BigInteger;

/** The key and host come exclusively from the player's currently open native amount menu. */
public record ConfirmBigAmountPacket(
        int containerId, BigInteger amount, boolean missing, boolean autoStart)
         {
    public void write(FriendlyByteBuf b) {
        b.writeVarInt(containerId);
        b.writeByteArray(BigAmounts.nonNegative(amount).toByteArray());
        b.writeBoolean(missing);
        b.writeBoolean(autoStart);
    }

    public static ConfirmBigAmountPacket read(FriendlyByteBuf b) {
        return new ConfirmBigAmountPacket(
                b.readVarInt(),
                BigAmounts.nonNegative(new BigInteger(b.readByteArray(2049))),
                b.readBoolean(),
                b.readBoolean());
    }


    public static void handle(ConfirmBigAmountPacket p, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> context) {
        var c = context.get();
        c.enqueueWork(
                () -> {
                    if (c.getSender() == null) return;
                    var m = c.getSender().containerMenu;
                    if (m.containerId == p.containerId && m instanceof BigAmountMenu big)
                        big.ae2lt$confirm(p.amount, p.missing, p.autoStart);
                });
        c.setPacketHandled(true);
    }
}
